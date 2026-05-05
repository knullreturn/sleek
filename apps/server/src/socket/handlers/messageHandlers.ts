import { Server } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { AuthSocket, memberSelect, messageInclude, serializeMessage } from '../socketTypes';
import { sendFcmPush } from '../../lib/firebase';

/**
 * Registers send / edit / delete / pin / unpin handlers.
 * Each handler validates membership, writes to DB, then broadcasts to the chat room.
 * send_message also ensures all member sockets are in the room (handles new chats).
 */
export function registerMessageHandlers(
  io:     Server,
  socket: AuthSocket,
  prisma: PrismaClient,
) {
  // ── send_message ────────────────────────────────────────────────────────────
  socket.on('send_message', async (
    payload: { chatId: string; content: string; replyToId?: string },
    ack?: (res: { ok: boolean; error?: string }) => void,
  ) => {
    try {
      const { chatId, content, replyToId } = payload;
      const trimmed = content?.trim();
      const fail = (error: string) => {
        socket.emit('error', { message: error });
        ack?.({ ok: false, error });
      };
      if (!trimmed)              return fail('Empty message');
      if (trimmed.length > 4000) return fail('Message too long (max 4000 characters)');

      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: socket.userId } },
      });
      if (!member) return fail('Not a member of this chat');

      if (replyToId) {
        const replyMsg = await prisma.message.findUnique({ where: { id: replyToId }, select: { chatId: true } });
        if (!replyMsg || replyMsg.chatId !== chatId) return fail('Invalid reply target');
      }

      const message = await prisma.message.create({
        data:    { chatId, senderId: socket.userId, content: trimmed, ...(replyToId ? { replyToId } : {}) },
        include: messageInclude,
      });

      await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });

      // Ensure ALL members' sockets are in the room — handles brand-new chats
      const allMembers = await prisma.chatMember.findMany({ where: { chatId }, select: { userId: true } });
      let chatForNewMembers: any = null;

      for (const { userId } of allMembers) {
        const userSockets = await io.in(`user:${userId}`).fetchSockets();
        for (const sock of userSockets) {
          if (!sock.rooms.has(`chat:${chatId}`)) {
            sock.join(`chat:${chatId}`);
            if (!chatForNewMembers) {
              chatForNewMembers = await prisma.chat.findUnique({
                where:   { id: chatId },
                include: { members: { include: { user: { select: memberSelect } } } },
              });
            }
            if (chatForNewMembers) {
              sock.emit('new_chat', {
                chat: {
                  id:          chatForNewMembers.id,
                  type:        chatForNewMembers.type,
                  createdAt:   chatForNewMembers.createdAt.toISOString(),
                  members:     chatForNewMembers.members.map((m: any) => ({ ...m.user, handle: m.user.tag })),
                  lastMessage: serializeMessage(message),
                },
              });
            }
          }
        }
      }

      io.to(`chat:${chatId}`).emit('receive_message', { message: serializeMessage(message) });

      // ACK success — client knows the message reached the server
      ack?.({ ok: true });

      // ── FCM push for offline members ───────────────────────────────────────
      const senderName = message.sender?.username ?? 'Someone';
      const chatMembers = await prisma.chatMember.findMany({
        where:   { chatId },
        include: { user: { select: { id: true, username: true, fcmToken: true } } },
      });
      const offlineTokens: string[] = [];
      for (const cm of chatMembers) {
        if (cm.user.id === socket.userId) continue;
        const activeSockets = await io.in(`user:${cm.user.id}`).fetchSockets();
        if (activeSockets.length === 0 && cm.user.fcmToken) {
          offlineTokens.push(cm.user.fcmToken);
        }
      }
      if (offlineTokens.length > 0) {
        await sendFcmPush(offlineTokens, {
          senderName,
          content:  message.deletedAt ? 'Deleted a message' : trimmed,
          chatId,
          chatName: senderName,
        });
      }

    } catch (err) {
      console.error('send_message error:', err);
      socket.emit('error', { message: 'Failed to send message' });
      ack?.({ ok: false, error: 'Server error' });
    }
  });

  // ── edit_message ────────────────────────────────────────────────────────────
  socket.on('edit_message', async (payload: { messageId: string; chatId: string; newContent: string }) => {
    try {
      const { messageId, newContent } = payload;
      const trimmed = newContent?.trim();
      if (!trimmed)              return;
      if (trimmed.length > 4000) return socket.emit('error', { message: 'Message too long' });

      const existing = await prisma.message.findUnique({ where: { id: messageId } });
      if (!existing)                          return socket.emit('error', { message: 'Message not found' });
      if (existing.senderId !== socket.userId) return socket.emit('error', { message: 'Not your message' });
      if (existing.deletedAt)                 return socket.emit('error', { message: 'Cannot edit a deleted message' });

      // ✅ Security: verify caller is a member of the message's REAL chat (not client-supplied chatId)
      const realChatId = existing.chatId;
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId: realChatId, userId: socket.userId } },
      });
      if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

      const updated = await prisma.message.update({
        where:   { id: messageId },
        data:    { content: trimmed, edited: true, originalContent: existing.originalContent ?? existing.content },
        include: messageInclude,
      });
      io.to(`chat:${realChatId}`).emit('message_edited', { message: serializeMessage(updated) });
    } catch (err) {
      console.error('edit_message error:', err);
      socket.emit('error', { message: 'Failed to edit message' });
    }
  });

  // ── delete_message (soft) ───────────────────────────────────────────────────
  socket.on('delete_message', async (payload: { messageId: string; chatId: string }) => {
    try {
      const { messageId } = payload;
      const existing = await prisma.message.findUnique({ where: { id: messageId } });
      if (!existing)                          return socket.emit('error', { message: 'Message not found' });
      if (existing.senderId !== socket.userId) return socket.emit('error', { message: 'Not your message' });
      if (existing.deletedAt)                 return; // idempotent

      // ✅ Security: verify caller is a member of the message's REAL chat
      const realChatId = existing.chatId;
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId: realChatId, userId: socket.userId } },
      });
      if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

      const updated = await prisma.message.update({
        where:   { id: messageId },
        data:    { deletedAt: new Date(), pinned: false, pinnedAt: null, pinnedById: null },
        include: messageInclude,
      });
      io.to(`chat:${realChatId}`).emit('message_deleted', { message: serializeMessage(updated) });
    } catch (err) {
      console.error('delete_message error:', err);
      socket.emit('error', { message: 'Failed to delete message' });
    }
  });

  // ── pin_message ─────────────────────────────────────────────────────────────
  socket.on('pin_message', async (payload: { messageId: string; chatId: string }) => {
    try {
      const { messageId } = payload;
      // ✅ Security: look up the message first, verify membership in its REAL chatId
      const existing = await prisma.message.findUnique({ where: { id: messageId } });
      if (!existing) return socket.emit('error', { message: 'Message not found' });
      const realChatId = existing.chatId;
      const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId: realChatId, userId: socket.userId } } });
      if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

      const updated = await prisma.message.update({
        where:   { id: messageId },
        data:    { pinned: true, pinnedAt: new Date(), pinnedById: socket.userId },
        include: messageInclude,
      });
      io.to(`chat:${realChatId}`).emit('message_pinned', { message: serializeMessage(updated) });
    } catch (err) {
      console.error('pin_message error:', err);
      socket.emit('error', { message: 'Failed to pin message' });
    }
  });

  // ── unpin_message ───────────────────────────────────────────────────────────
  socket.on('unpin_message', async (payload: { messageId: string; chatId: string }) => {
    try {
      const { messageId } = payload;
      // ✅ Security: look up the message first, verify membership in its REAL chatId
      const existing = await prisma.message.findUnique({ where: { id: messageId } });
      if (!existing) return socket.emit('error', { message: 'Message not found' });
      const realChatId = existing.chatId;
      const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId: realChatId, userId: socket.userId } } });
      if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

      const updated = await prisma.message.update({
        where:   { id: messageId },
        data:    { pinned: false, pinnedAt: null, pinnedById: null },
        include: messageInclude,
      });
      io.to(`chat:${realChatId}`).emit('message_unpinned', { message: serializeMessage(updated) });
    } catch (err) {
      console.error('unpin_message error:', err);
      socket.emit('error', { message: 'Failed to unpin message' });
    }
  });
}
