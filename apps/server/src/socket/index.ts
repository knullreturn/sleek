import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';
import { verifyJwt } from '../lib/jwt';
import { AuthSocket } from './socketTypes';
import { registerMessageHandlers } from './handlers/messageHandlers';
import { registerPresenceHandlers, broadcastPresence } from './handlers/presenceHandlers';

// In-memory online tracker: userId → set of socketIds (multi-tab/device safe)
const onlineUsers = new Map<string, Set<string>>();

// In-memory sleep mode: userId → true when user has sleep mode on
// Resets naturally when user disconnects (correct — sleep mode is a "do not disturb now" intent)
const sleepingUsers = new Set<string>();

/** Returns the set of userIds currently online */
export function getOnlineUserIds(): string[] {
  return [...onlineUsers.keys()];
}

/** Returns whether a userId has sleep mode enabled server-side */
export function isUserSleeping(userId: string): boolean {
  return sleepingUsers.has(userId);
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

  // ── Connection ───────────────────────────────────────────────────────────────
  io.on('connection', (socket: Socket) => {
    const s = socket as AuthSocket;

    // Track multi-tab presence
    if (!onlineUsers.has(s.userId)) onlineUsers.set(s.userId, new Set());
    onlineUsers.get(s.userId)!.add(s.id);
    broadcastPresence(io, prisma, s.userId, 'online');

    // ── Fix: presence_snapshot ────────────────────────────────────────────────
    // Send the full set of currently-online userIds + their sleep status to this
    // client immediately. Fixes "shows offline forever" when peer was already online.
    const onlineIds = getOnlineUserIds();
    socket.emit('presence_snapshot', {
      onlineUserIds: onlineIds,
      sleepingUserIds: [...sleepingUsers].filter(id => onlineUsers.has(id)),
    });

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
      if (member) {
        socket.join(`chat:${chatId}`);
        // Send presence_snapshot scoped to this chat's members
        const chatMembers = await prisma.chatMember.findMany({
          where:  { chatId },
          select: { userId: true },
        });
        const chatOnlineIds  = chatMembers.map(m => m.userId).filter(uid => onlineUsers.has(uid));
        const chatSleepIds   = chatOnlineIds.filter(uid => sleepingUsers.has(uid));
        socket.emit('presence_snapshot', { onlineUserIds: chatOnlineIds, sleepingUserIds: chatSleepIds });
      }
    });

    socket.on('leave_chat', (chatId: string) => socket.leave(`chat:${chatId}`));

    registerMessageHandlers(io, s, prisma);
    registerPresenceHandlers(io, s, prisma);

    // ── Sleep mode toggle ─────────────────────────────────────────────────────
    // Client emits this when user toggles sleep mode in settings.
    // We track it server-side and broadcast as a presence update so peers
    // see the change instantly without any polling.
    socket.on('set_sleep_mode', (payload: { enabled: boolean }) => {
      if (payload.enabled) {
        sleepingUsers.add(s.userId);
      } else {
        sleepingUsers.delete(s.userId);
      }
      // Re-broadcast presence — peers will receive status: 'sleeping' | 'online'
      broadcastPresence(io, prisma, s.userId, payload.enabled ? 'sleeping' : 'online');
    });

    // ── Disconnect ────────────────────────────────────────────────────────────
    socket.on('disconnect', async () => {
      const sockets = onlineUsers.get(s.userId);
      if (sockets) {
        sockets.delete(s.id);
        if (sockets.size === 0) {
          onlineUsers.delete(s.userId);
          sleepingUsers.delete(s.userId);  // clear sleep state — resets on next connect
          broadcastPresence(io, prisma, s.userId, 'offline');
        }
      }
    });
  });
}
