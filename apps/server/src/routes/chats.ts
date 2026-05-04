import type { FastifyInstance } from 'fastify';
import { prisma } from '../lib/prisma';
import { authenticate } from '../lib/auth.middleware';
import { z } from 'zod';

const memberSelect = { id: true, username: true, tag: true, avatarUrl: true };

const fmtUser = (u: any) => ({ ...u, handle: u.tag });  // handle is just the 7-char tag

function serializeMessage(msg: any) {
  return {
    id:              msg.id,
    chatId:          msg.chatId,
    senderId:        msg.senderId,
    sender:          fmtUser(msg.sender),
    content:         msg.content,
    edited:          msg.edited          ?? false,
    originalContent: msg.originalContent ?? null,
    deletedAt:       msg.deletedAt       ? msg.deletedAt.toISOString()  : null,
    pinned:          msg.pinned          ?? false,
    pinnedAt:        msg.pinnedAt        ? msg.pinnedAt.toISOString()   : null,
    pinnedById:      msg.pinnedById      ?? null,
    pinnedBy:        msg.pinnedBy        ? fmtUser(msg.pinnedBy) : null,
    replyToId:       msg.replyToId       ?? null,
    replyTo: msg.replyTo ? {
      id:        msg.replyTo.id,
      content:   msg.replyTo.content,
      deletedAt: msg.replyTo.deletedAt ? msg.replyTo.deletedAt.toISOString() : null,
      sender:    fmtUser(msg.replyTo.sender),
    } : null,
    createdAt: msg.createdAt.toISOString(),
    updatedAt: msg.updatedAt.toISOString(),
  };
}

export async function chatRoutes(app: FastifyInstance) {
  // GET /api/chats
  app.get('/', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const chatMembers = await prisma.chatMember.findMany({
      where: { userId: me.id },
      include: {
        chat: {
          include: {
            members: { include: { user: { select: memberSelect } } },
            messages: { orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: memberSelect } } },
            _count: { select: { messages: true } },
          },
        },
      },
      orderBy: { chat: { updatedAt: 'desc' } },
    });

    // Compute unread counts in parallel
    const withUnread = await Promise.all(
      chatMembers.map(async (cm) => {
        const unreadCount = await prisma.message.count({
          where: {
            chatId:    cm.chatId,
            senderId:  { not: me.id },
            createdAt: { gt: cm.lastReadAt },
          },
        });
        return { chat: cm.chat, unreadCount };
      }),
    );

    return reply.send(withUnread.map(({ chat, unreadCount }) => ({
      id: chat.id,
      type: chat.type,
      createdAt: chat.createdAt.toISOString(),
      members: chat.members.map((m) => fmtUser(m.user)),
      lastMessage: chat.messages[0] ? serializeMessage(chat.messages[0]) : null,
      unreadCount,
    })));
  });

  // PUT /api/chats/:id/read — mark all messages as read
  app.put('/:id/read', { preHandler: authenticate }, async (request, reply) => {
    const me     = (request as any).user;
    const chatId = (request.params as any).id;
    await prisma.chatMember.updateMany({
      where: { chatId, userId: me.id },
      data:  { lastReadAt: new Date() },
    });
    return reply.send({ ok: true });
  });

  // POST /api/chats
  app.post('/', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ targetUserId: z.string().cuid() });
    const parse = schema.safeParse(request.body);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'targetUserId required', statusCode: 400 });

    const me = (request as any).user;
    const { targetUserId } = parse.data;
    if (me.id === targetUserId) return reply.status(400).send({ error: 'Bad Request', message: 'Cannot chat with yourself', statusCode: 400 });

    const existing = await prisma.chat.findFirst({
      where: {
        type: 'DM',
        AND: [
          { members: { some: { userId: me.id } } },
          { members: { some: { userId: targetUserId } } },
        ],
      },
      include: {
        members: { include: { user: { select: memberSelect } } },
        messages: { orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: memberSelect } } },
      },
    });

    if (existing) {
      return reply.send({
        id: existing.id, type: existing.type,
        createdAt: existing.createdAt.toISOString(),
        members: existing.members.map((m) => fmtUser(m.user)),
        lastMessage: existing.messages[0] ? serializeMessage(existing.messages[0]) : null,
      });
    }

    const chat = await prisma.chat.create({
      data: { type: 'DM', members: { create: [{ userId: me.id }, { userId: targetUserId }] } },
      include: { members: { include: { user: { select: memberSelect } } } },
    });

    return reply.status(201).send({
      id: chat.id, type: chat.type,
      createdAt: chat.createdAt.toISOString(),
      members: chat.members.map((m) => fmtUser(m.user)),
      lastMessage: null,
    });
  });

  // GET /api/chats/:id/messages
  app.get('/:id/messages', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const { id } = request.params as { id: string };
    const query = request.query as { cursor?: string; limit?: string };
    const limit = Math.min(parseInt(query.limit || '50', 10), 100);

    const member = await prisma.chatMember.findUnique({
      where: { chatId_userId: { chatId: id, userId: me.id } },
    });
    if (!member) return reply.status(403).send({ error: 'Forbidden', message: 'Not a member', statusCode: 403 });

    const messages = await prisma.message.findMany({
      where:   { chatId: id, ...(query.cursor ? { createdAt: { lt: new Date(query.cursor) } } : {}) },
      include: {
        sender:   { select: memberSelect },
        replyTo:  { include: { sender: { select: memberSelect } } },
        pinnedBy: { select: memberSelect },
      },
      orderBy: { createdAt: 'desc' },
      take: limit + 1,
    });

    const hasMore = messages.length > limit;
    const result = hasMore ? messages.slice(0, limit) : messages;

    return reply.send({
      messages: result.reverse().map(serializeMessage),
      hasMore,
      nextCursor: hasMore ? result[0].createdAt.toISOString() : undefined,
    });
  });
  // GET /api/chats/:id/pins
  app.get('/:id/pins', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const { id } = request.params as { id: string };

    const member = await prisma.chatMember.findUnique({
      where: { chatId_userId: { chatId: id, userId: me.id } },
    });
    if (!member) return reply.status(403).send({ error: 'Forbidden', statusCode: 403 });

    const pinned = await prisma.message.findMany({
      where:   { chatId: id, pinned: true, deletedAt: null },  // exclude soft-deleted
      include: {
        sender:   { select: memberSelect },
        replyTo:  { include: { sender: { select: memberSelect } } },
        pinnedBy: { select: memberSelect },
      },
      orderBy: { pinnedAt: 'desc' },
    });

    return reply.send({ pins: pinned.map(serializeMessage) });
  });
}
