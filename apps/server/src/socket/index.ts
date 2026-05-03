import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { verifyJwt } from '../lib/jwt';

interface AuthSocket extends Socket {
  userId:   string;
  username: string | null;
  tag:      string;
}

const memberSelect = { id: true, username: true, tag: true, avatarUrl: true };
const onlineUsers  = new Map<string, Set<string>>();

// Shared include shape used across all message queries
const messageInclude = {
  sender:   { select: memberSelect },
  replyTo:  { include: { sender: { select: memberSelect } } },
  pinnedBy: { select: memberSelect },
};

function serializeMessage(msg: any) {
  return {
    id:              msg.id,
    chatId:          msg.chatId,
    senderId:        msg.senderId,
    sender:          { ...msg.sender, handle: msg.sender.tag },
    content:         msg.content,
    edited:          msg.edited,
    originalContent: msg.originalContent  ?? null,
    deletedAt:       msg.deletedAt        ? msg.deletedAt.toISOString()  : null,
    pinned:          msg.pinned,
    pinnedAt:        msg.pinnedAt         ? msg.pinnedAt.toISOString()   : null,
    pinnedById:      msg.pinnedById       ?? null,
    pinnedBy:        msg.pinnedBy ? { ...msg.pinnedBy, handle: msg.pinnedBy.tag } : null,
    replyTo: msg.replyTo ? {
      id:        msg.replyTo.id,
      content:   msg.replyTo.content,
      deletedAt: msg.replyTo.deletedAt ? msg.replyTo.deletedAt.toISOString() : null,
      sender:    { ...msg.replyTo.sender, handle: msg.replyTo.sender.tag },
    } : null,
    createdAt: msg.createdAt.toISOString(),
    updatedAt: msg.updatedAt.toISOString(),
  };
}

export function registerSocketServer(io: Server, prisma: PrismaClient) {
  // ── Auth middleware ──────────────────────────────────────────────────────────
  io.use(async (socket, next) => {
    try {
      const token =
        (socket.handshake.auth as any).token ||
        socket.handshake.headers.authorization?.replace('Bearer ', '');
      if (!token) return next(new Error('Authentication required'));

      const payload = await verifyJwt(token);
      const user    = await prisma.user.findUnique({
        where:  { id: payload.sub as string },
        select: { id: true, username: true, tag: true },
      });
      if (!user) return next(new Error('User not found'));

      (socket as AuthSocket).userId   = user.id;
      (socket as AuthSocket).username = user.username ?? null;
      (socket as AuthSocket).tag      = user.tag;
      next();
    } catch {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const s = socket as AuthSocket;

    // Track multi-tab presence
    if (!onlineUsers.has(s.userId)) onlineUsers.set(s.userId, new Set());
    onlineUsers.get(s.userId)!.add(s.id);
    broadcastPresence(io, prisma, s.userId, 'online');

    // ── Room management ────────────────────────────────────────────────────────
    socket.on('join_chat', async (chatId: string) => {
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: s.userId } },
      });
      if (member) socket.join(`chat:${chatId}`);
    });

    socket.on('leave_chat', (chatId: string) => socket.leave(`chat:${chatId}`));

    // ── send_message ──────────────────────────────────────────────────────────
    socket.on('send_message', async (payload: { chatId: string; content: string; replyToId?: string }) => {
      try {
        const { chatId, content, replyToId } = payload;
        const trimmed = content?.trim();
        if (!trimmed)            return;
        if (trimmed.length > 4000) return socket.emit('error', { message: 'Message too long (max 4000 characters)' });

        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        // Validate reply target belongs to the same chat
        if (replyToId) {
          const replyMsg = await prisma.message.findUnique({ where: { id: replyToId }, select: { chatId: true } });
          if (!replyMsg || replyMsg.chatId !== chatId) return socket.emit('error', { message: 'Invalid reply target' });
        }

        const message = await prisma.message.create({
          data:    { chatId, senderId: s.userId, content: trimmed, ...(replyToId ? { replyToId } : {}) },
          include: messageInclude,
        });

        await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });
        io.to(`chat:${chatId}`).emit('receive_message', { message: serializeMessage(message) });
      } catch (err) {
        console.error('send_message error:', err);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    // ── edit_message ──────────────────────────────────────────────────────────
    socket.on('edit_message', async (payload: { messageId: string; chatId: string; newContent: string }) => {
      try {
        const { messageId, chatId, newContent } = payload;
        const trimmed = newContent?.trim();
        if (!trimmed)              return;
        if (trimmed.length > 4000) return socket.emit('error', { message: 'Message too long (max 4000 characters)' });

        const existing = await prisma.message.findUnique({ where: { id: messageId } });
        if (!existing)                      return socket.emit('error', { message: 'Message not found' });
        if (existing.senderId !== s.userId) return socket.emit('error', { message: 'Not your message' });
        if (existing.deletedAt)             return socket.emit('error', { message: 'Cannot edit a deleted message' });

        const updated = await prisma.message.update({
          where:   { id: messageId },
          data:    {
            content:         trimmed,
            edited:          true,
            // Preserve the very first version of the content; never overwrite
            originalContent: existing.originalContent ?? existing.content,
          },
          include: messageInclude,
        });
        io.to(`chat:${chatId}`).emit('message_edited', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('edit_message error:', err);
        socket.emit('error', { message: 'Failed to edit message' });
      }
    });

    // ── delete_message (soft) ─────────────────────────────────────────────────
    socket.on('delete_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const existing = await prisma.message.findUnique({ where: { id: messageId } });
        if (!existing)                      return socket.emit('error', { message: 'Message not found' });
        if (existing.senderId !== s.userId) return socket.emit('error', { message: 'Not your message' });
        if (existing.deletedAt)             return; // already deleted — idempotent

        const updated = await prisma.message.update({
          where:   { id: messageId },
          data:    {
            deletedAt:  new Date(),
            // Auto-unpin: a deleted message should not remain pinned
            pinned:     false,
            pinnedAt:   null,
            pinnedById: null,
          },
          include: messageInclude,
        });
        io.to(`chat:${chatId}`).emit('message_deleted', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('delete_message error:', err);
        socket.emit('error', { message: 'Failed to delete message' });
      }
    });

    // ── pin_message ───────────────────────────────────────────────────────────
    socket.on('pin_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        const updated = await prisma.message.update({
          where:   { id: messageId },
          data:    { pinned: true, pinnedAt: new Date(), pinnedById: s.userId },
          include: messageInclude,
        });
        io.to(`chat:${chatId}`).emit('message_pinned', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('pin_message error:', err);
        socket.emit('error', { message: 'Failed to pin message' });
      }
    });

    // ── unpin_message ─────────────────────────────────────────────────────────
    socket.on('unpin_message', async (payload: { messageId: string; chatId: string }) => {
      try {
        const { messageId, chatId } = payload;
        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        const updated = await prisma.message.update({
          where:   { id: messageId },
          data:    { pinned: false, pinnedAt: null, pinnedById: null },
          include: messageInclude,
        });
        io.to(`chat:${chatId}`).emit('message_unpinned', { message: serializeMessage(updated) });
      } catch (err) {
        console.error('unpin_message error:', err);
        socket.emit('error', { message: 'Failed to unpin message' });
      }
    });

    // ── Typing indicator ──────────────────────────────────────────────────────
    socket.on('typing', (payload: { chatId: string; isTyping: boolean }) => {
      socket.to(`chat:${payload.chatId}`).emit('typing', {
        chatId:   payload.chatId,
        userId:   s.userId,
        username: s.username,
        isTyping: payload.isTyping,
      });
    });

    // ── Read receipts ─────────────────────────────────────────────────────────
    socket.on('mark_seen', (payload: { chatId: string; messageId: string }) => {
      socket.to(`chat:${payload.chatId}`).emit('message_seen', {
        messageId: payload.messageId,
        chatId:    payload.chatId,
        userId:    s.userId,
      });
    });

    // ── Disconnect ────────────────────────────────────────────────────────────
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

async function broadcastPresence(
  io: Server, prisma: PrismaClient, userId: string, status: 'online' | 'offline',
) {
  const memberships = await prisma.chatMember.findMany({ where: { userId }, select: { chatId: true } });
  for (const { chatId } of memberships) {
    io.to(`chat:${chatId}`).emit('presence', { userId, status });
  }
}
