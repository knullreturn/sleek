import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { verifyJwt } from '../lib/jwt';

interface AuthSocket extends Socket {
  userId: string;
  username: string | null;
  tag: string;
}

const memberSelect = { id: true, username: true, tag: true, avatarUrl: true };
const onlineUsers  = new Map<string, Set<string>>();

function serializeMessage(message: any) {
  return {
    id:              message.id,
    chatId:          message.chatId,
    senderId:        message.senderId,
    sender:          { ...message.sender, handle: message.sender.tag },
    content:         message.content,
    edited:          message.edited,
    originalContent: message.originalContent  ?? null,
    deletedAt:       message.deletedAt        ? message.deletedAt.toISOString() : null,
    pinned:          message.pinned,
    pinnedAt:        message.pinnedAt         ? message.pinnedAt.toISOString()  : null,
    pinnedById:      message.pinnedById        ?? null,
    pinnedBy:        message.pinnedBy ? { ...message.pinnedBy, handle: message.pinnedBy.tag } : null,
    replyTo: message.replyTo ? {
      id:        message.replyTo.id,
      content:   message.replyTo.content,
      deletedAt: message.replyTo.deletedAt ? message.replyTo.deletedAt.toISOString() : null,
      sender:    { ...message.replyTo.sender, handle: message.replyTo.sender.tag },
    } : null,
    createdAt: message.createdAt.toISOString(),
    updatedAt: message.updatedAt.toISOString(),
  };
}


export function registerSocketServer(io: Server, prisma: PrismaClient) {
  // ── JWT Auth middleware ────────────────────────────────────────────────────
  io.use(async (socket, next) => {
    try {
      const token =
        (socket.handshake.auth as any).token ||
        socket.handshake.headers.authorization?.replace('Bearer ', '');
      if (!token) return next(new Error('Authentication required'));

      const payload = await verifyJwt(token);
      const user = await prisma.user.findUnique({
        where: { id: payload.sub as string },
        select: { id: true, username: true, tag: true },
      });
      if (!user) return next(new Error('User not found'));

      (socket as AuthSocket).userId = user.id;
      (socket as AuthSocket).username = user.username ?? null;
      (socket as AuthSocket).tag = user.tag;
      next();
    } catch {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const s = socket as AuthSocket;

    if (!onlineUsers.has(s.userId)) onlineUsers.set(s.userId, new Set());
    onlineUsers.get(s.userId)!.add(s.id);
    broadcastPresence(io, prisma, s.userId, 'online');

    socket.on('join_chat', async (chatId: string) => {
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: s.userId } },
      });
      if (member) socket.join(`chat:${chatId}`);
    });

    socket.on('leave_chat', (chatId: string) => socket.leave(`chat:${chatId}`));

    socket.on('send_message', async (payload: { chatId: string; content: string; replyToId?: string }) => {
      try {
        const { chatId, content, replyToId } = payload;
        // Fix #6: validate content
        if (!content?.trim()) return;
        if (content.trim().length > 4000) return socket.emit('error', { message: 'Message too long (max 4000 characters)' });

        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        // Fix #7: validate replyToId belongs to same chat
        if (replyToId) {
          const replyMsg = await prisma.message.findUnique({ where: { id: replyToId }, select: { chatId: true } });
          if (!replyMsg || replyMsg.chatId !== chatId) return socket.emit('error', { message: 'Invalid reply target' });
        }

        const message = await prisma.message.create({
          data: { chatId, senderId: s.userId, content: content.trim(), ...(replyToId ? { replyToId } : {}) },
          include: {
            sender:  { select: memberSelect },
            replyTo: { include: { sender: { select: memberSelect } } },
          },
        });

        await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });
        io.to(`chat:${chatId}`).emit('receive_message', { message: serializeMessage(message) });
      } catch (err) {
        console.error('send_message error:', err);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    socket.on('edit_message', async (payload: { messageId: string; chatId: string; newContent: string }) => {
      try {
        const { messageId, chatId, newContent } = payload;
        if (!newContent?.trim()) return;
        // Fix #6: validate content length
        if (newContent.trim().length > 4000) return socket.emit('error', { message: 'Message too long (max 4000 characters)' });

        const existing = await prisma.message.findUnique({ where: { id: messageId } });
        if (!existing)                      return socket.emit('error', { message: 'Message not found' });
        if (existing.senderId !== s.userId) return socket.emit('error', { message: 'Not your message' });
        if (existing.deletedAt)             return socket.emit('error', { message: 'Cannot edit a deleted message' });

        const updated = await prisma.message.update({
          where: { id: messageId },
          data: {
            content:         newContent.trim(),
            edited:          true,
            originalContent: existing.originalContent ?? existing.content,
          },
          include: {
            sender:  { select: memberSelect },
            replyTo: { include: { sender: { select: memberSelect } } },
            pinnedBy: { select: memberSelect },
          },
        });
        io.to(`chat:${chatId}`).emit('message_edited', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('edit_message error:', err);
        socket.emit('error', { message: 'Failed to edit message' });
      }
    });

    // Fix #4: Soft delete — only sender can delete; auto-unpins if pinned
    socket.on('delete_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const existing = await prisma.message.findUnique({ where: { id: messageId } });
        if (!existing)                      return socket.emit('error', { message: 'Message not found' });
        if (existing.senderId !== s.userId) return socket.emit('error', { message: 'Not your message' });
        if (existing.deletedAt)             return; // already deleted

        const updated = await prisma.message.update({
          where: { id: messageId },
          data: {
            deletedAt:  new Date(),
            // Auto-unpin on delete — a deleted pinned message is meaningless
            pinned:     false,
            pinnedAt:   null,
            pinnedById: null,
          },
          include: {
            sender:   { select: memberSelect },
            replyTo:  { include: { sender: { select: memberSelect } } },
            pinnedBy: { select: memberSelect },
          },
        });
        io.to(`chat:${chatId}`).emit('message_deleted', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('delete_message error:', err);
        socket.emit('error', { message: 'Failed to delete message' });
      }
    });

    socket.on('pin_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        const updated = await prisma.message.update({
          where: { id: messageId },
          data:  { pinned: true, pinnedAt: new Date(), pinnedById: s.userId },
          include: {
            sender:   { select: memberSelect },
            replyTo:  { include: { sender: { select: memberSelect } } },
            pinnedBy: { select: memberSelect },
          },
        });
        io.to(`chat:${chatId}`).emit('message_pinned', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('pin_message error:', err);
        socket.emit('error', { message: 'Failed to pin message' });
      }
    });

    socket.on('unpin_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        const updated = await prisma.message.update({
          where: { id: messageId },
          data:  { pinned: false, pinnedAt: null, pinnedById: null },
          include: {
            sender:   { select: memberSelect },
            replyTo:  { include: { sender: { select: memberSelect } } },
            pinnedBy: { select: memberSelect },
          },
        });
        io.to(`chat:${chatId}`).emit('message_unpinned', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('unpin_message error:', err);
        socket.emit('error', { message: 'Failed to unpin message' });
      }
    });

    socket.on('typing', (payload: { chatId: string; isTyping: boolean }) => {
      socket.to(`chat:${payload.chatId}`).emit('typing', {
        chatId: payload.chatId, userId: s.userId, username: s.username, isTyping: payload.isTyping,
      });
    });

    socket.on('mark_seen', (payload: { chatId: string; messageId: string }) => {
      socket.to(`chat:${payload.chatId}`).emit('message_seen', {
        messageId: payload.messageId, chatId: payload.chatId, userId: s.userId,
      });
    });

    socket.on('disconnect', async () => {
      const sockets = onlineUsers.get(s.userId);
      if (sockets) {
        sockets.delete(s.id);
        if (sockets.size === 0) {
          onlineUsers.delete(s.userId);
          broadcastPresence(io, prisma, s.userId, 'offline');
        }
      }
    });
  });
}

async function broadcastPresence(io: Server, prisma: PrismaClient, userId: string, status: 'online' | 'offline') {
  const memberships = await prisma.chatMember.findMany({ where: { userId }, select: { chatId: true } });
  for (const { chatId } of memberships) {
    io.to(`chat:${chatId}`).emit('presence', { userId, status });
  }
}
