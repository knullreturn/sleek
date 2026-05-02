import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { verifyJwt } from '../lib/jwt.js';

interface AuthSocket extends Socket {
  userId: string;
  username: string;
  tag: string;
}

const memberSelect = { id: true, username: true, tag: true, avatarUrl: true };

// In-memory presence (upgrade to Redis for multi-instance later)
const onlineUsers = new Map<string, Set<string>>(); // userId -> Set<socketId>

export function registerSocketServer(io: Server, prisma: PrismaClient) {
  // ── Auth middleware ────────────────────────────────────────────────────────
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
      (socket as AuthSocket).username = user.username;
      (socket as AuthSocket).tag = user.tag;
      next();
    } catch {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const s = socket as AuthSocket;
    console.log(`🔌 Connected: ${s.username}#${s.tag} (${s.id})`);

    // Track presence
    if (!onlineUsers.has(s.userId)) onlineUsers.set(s.userId, new Set());
    onlineUsers.get(s.userId)!.add(s.id);

    // Broadcast online status to all users in shared chats
    broadcastPresence(io, prisma, s.userId, 'online');

    // ── join_chat ──────────────────────────────────────────────────────────
    socket.on('join_chat', async (chatId: string) => {
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: s.userId } },
      });
      if (member) socket.join(`chat:${chatId}`);
    });

    // ── leave_chat ─────────────────────────────────────────────────────────
    socket.on('leave_chat', (chatId: string) => {
      socket.leave(`chat:${chatId}`);
    });

    // ── send_message ───────────────────────────────────────────────────────
    socket.on('send_message', async (payload: { chatId: string; content: string; replyToId?: string }) => {
      try {
        const { chatId, content, replyToId } = payload;

        if (!content?.trim()) return;

        // Verify membership
        const member = await prisma.chatMember.findUnique({
          where: { chatId_userId: { chatId, userId: s.userId } },
        });
        if (!member) return socket.emit('error', { message: 'Not a member of this chat' });

        // Persist
        const message = await prisma.message.create({
          data: {
            chatId,
            senderId: s.userId,
            content: content.trim(),
            ...(replyToId ? { replyToId } : {}),
          },
          include: {
            sender: { select: memberSelect },
            replyTo: { include: { sender: { select: memberSelect } } },
          },
        });

        // Update chat updatedAt
        await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });

        const serialized = {
          id: message.id,
          chatId: message.chatId,
          senderId: message.senderId,
          sender: { ...message.sender, handle: `${message.sender.username}#${message.sender.tag}` },
          content: message.content,
          replyTo: message.replyTo
            ? {
                id: message.replyTo.id,
                content: message.replyTo.content,
                sender: {
                  ...message.replyTo.sender,
                  handle: `${message.replyTo.sender.username}#${message.replyTo.sender.tag}`,
                },
              }
            : null,
          createdAt: message.createdAt.toISOString(),
          updatedAt: message.updatedAt.toISOString(),
        };

        // Broadcast to all chat members
        io.to(`chat:${chatId}`).emit('receive_message', { message: serialized });
      } catch (err) {
        console.error('send_message error:', err);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    // ── typing ─────────────────────────────────────────────────────────────
    socket.on('typing', (payload: { chatId: string; isTyping: boolean }) => {
      socket.to(`chat:${payload.chatId}`).emit('typing', {
        chatId: payload.chatId,
        userId: s.userId,
        username: s.username,
        isTyping: payload.isTyping,
      });
    });

    // ── mark_seen ──────────────────────────────────────────────────────────
    socket.on('mark_seen', (payload: { chatId: string; messageId: string }) => {
      socket.to(`chat:${payload.chatId}`).emit('message_seen', {
        messageId: payload.messageId,
        chatId: payload.chatId,
        userId: s.userId,
      });
    });

    // ── disconnect ─────────────────────────────────────────────────────────
    socket.on('disconnect', async () => {
      const sockets = onlineUsers.get(s.userId);
      if (sockets) {
        sockets.delete(s.id);
        if (sockets.size === 0) {
          onlineUsers.delete(s.userId);
          broadcastPresence(io, prisma, s.userId, 'offline');
        }
      }
      console.log(`🔌 Disconnected: ${s.username}#${s.tag}`);
    });
  });
}

async function broadcastPresence(
  io: Server,
  prisma: PrismaClient,
  userId: string,
  status: 'online' | 'offline'
) {
  // Find all chats this user is in and broadcast to those rooms
  const memberships = await prisma.chatMember.findMany({
    where: { userId },
    select: { chatId: true },
  });

  for (const { chatId } of memberships) {
    io.to(`chat:${chatId}`).emit('presence', { userId, status });
  }
}
