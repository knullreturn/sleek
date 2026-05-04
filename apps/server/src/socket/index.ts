import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { verifyJwt } from '../lib/jwt';
import { AuthSocket } from './socketTypes';
import { registerMessageHandlers } from './handlers/messageHandlers';
import { registerPresenceHandlers, broadcastPresence } from './handlers/presenceHandlers';

const onlineUsers = new Map<string, Set<string>>();

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

  // ── Connection ───────────────────────────────────────────────────────────────
  io.on('connection', (socket: Socket) => {
    const s = socket as AuthSocket;

    // Track multi-tab presence
    if (!onlineUsers.has(s.userId)) onlineUsers.set(s.userId, new Set());
    onlineUsers.get(s.userId)!.add(s.id);
    broadcastPresence(io, prisma, s.userId, 'online');

    // Personal room — lets send_message find and join this socket into new chat rooms
    socket.join(`user:${s.userId}`);

    // Auto-join ALL existing chat rooms so messages arrive regardless of which
    // screen is open (no need to emit join_chat for message delivery)
    prisma.chatMember
      .findMany({ where: { userId: s.userId }, select: { chatId: true } })
      .then((memberships) => { for (const { chatId } of memberships) socket.join(`chat:${chatId}`); })
      .catch((err) => console.error('auto-join error:', err));

    // ── Room management (fine-grained, e.g. new chat created while online) ────
    socket.on('join_chat', async (chatId: string) => {
      const member = await prisma.chatMember.findUnique({
        where: { chatId_userId: { chatId, userId: s.userId } },
      });
      if (member) socket.join(`chat:${chatId}`);
    });

    socket.on('leave_chat', (chatId: string) => socket.leave(`chat:${chatId}`));

    // ── Feature handlers ──────────────────────────────────────────────────────
    registerMessageHandlers(io, s, prisma);
    registerPresenceHandlers(io, s, prisma);

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
