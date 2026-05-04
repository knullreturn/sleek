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
  socket.on('typing', (payload: { chatId: string; isTyping: boolean }) => {
    socket.to(`chat:${payload.chatId}`).emit('typing', {
      chatId:   payload.chatId,
      userId:   socket.userId,
      username: socket.username,
      isTyping: payload.isTyping,
    });
  });

  // ── Read receipts ───────────────────────────────────────────────────────────
  socket.on('mark_seen', (payload: { chatId: string; messageId: string }) => {
    socket.to(`chat:${payload.chatId}`).emit('message_seen', {
      messageId: payload.messageId,
      chatId:    payload.chatId,
      userId:    socket.userId,
    });
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
