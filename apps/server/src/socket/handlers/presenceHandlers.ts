import { Server } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { AuthSocket } from '../socketTypes';

/** Typing indicator, read receipts, and presence broadcast. */
export function registerPresenceHandlers(
  io:     Server,
  socket: AuthSocket,
  prisma: PrismaClient,
) {
  // ── Typing indicator ────────────────────────────────────────────────────────
  // ✅ Security: verify caller is a member of the supplied chatId before broadcasting
  socket.on('typing', async (payload: { chatId: string; isTyping: boolean }) => {
    try {
      const { chatId, isTyping } = payload;
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: socket.userId } },
      });
      if (!member) return; // silently ignore — don't reveal chat existence
      socket.to(`chat:${chatId}`).emit('typing', {
        chatId,
        userId:   socket.userId,
        username: socket.username,
        isTyping,
      });
    } catch {
      // swallow — typing errors must never affect the socket connection
    }
  });

  // ── Read receipts ───────────────────────────────────────────────────────────
  // ✅ Security: verify caller is a member of the chat and owns the message before broadcasting
  socket.on('mark_seen', async (payload: { chatId: string; messageId: string }) => {
    try {
      const { chatId, messageId } = payload;

      // Verify membership in the supplied chatId
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: socket.userId } },
      });
      if (!member) return;

      // Verify the message actually belongs to this chat
      const message = await prisma.message.findFirst({
        where: { id: messageId, chatId },
        select: { id: true },
      });
      if (!message) return;

      socket.to(`chat:${chatId}`).emit('message_seen', {
        messageId,
        chatId,
        userId: socket.userId,
      });
    } catch {
      // swallow
    }
  });
}

/** Broadcast online/offline status to all rooms this user is a member of. */
export async function broadcastPresence(
  io:     Server,
  prisma: PrismaClient,
  userId: string,
  status: 'online' | 'offline',
) {
  const memberships = await prisma.chatMember.findMany({ where: { userId }, select: { chatId: true } });
  for (const { chatId } of memberships) {
    io.to(`chat:${chatId}`).emit('presence', { userId, status });
  }
}
