import type { FastifyInstance } from 'fastify';
import { PrismaClient } from '@prisma/client';
import { authenticate } from '../lib/auth.middleware.js';
import { z } from 'zod';

const prisma = new PrismaClient();

const memberSelect = {
  id: true,
  username: true,
  tag: true,
  avatarUrl: true,
};

function serializeMessage(msg: any) {
  return {
    id: msg.id,
    chatId: msg.chatId,
    senderId: msg.senderId,
    sender: { ...msg.sender, handle: `${msg.sender.username}#${msg.sender.tag}` },
    content: msg.content,
    replyToId: msg.replyToId,
    replyTo: msg.replyTo
      ? {
          id: msg.replyTo.id,
          content: msg.replyTo.content,
          sender: { ...msg.replyTo.sender, handle: `${msg.replyTo.sender.username}#${msg.replyTo.sender.tag}` },
        }
      : null,
    createdAt: msg.createdAt.toISOString(),
    updatedAt: msg.updatedAt.toISOString(),
  };
}

export async function chatRoutes(app: FastifyInstance) {
  // GET /api/chats — get all chats for current user
  app.get('/', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;

    const chatMembers = await prisma.chatMember.findMany({
      where: { userId: me.id },
      include: {
        chat: {
          include: {
            members: { include: { user: { select: memberSelect } } },
            messages: {
              orderBy: { createdAt: 'desc' },
              take: 1,
              include: { sender: { select: memberSelect } },
            },
          },
        },
      },
      orderBy: { chat: { updatedAt: 'desc' } },
    });

    const chats = chatMembers.map(({ chat }) => ({
      id: chat.id,
      type: chat.type,
      createdAt: chat.createdAt.toISOString(),
      members: chat.members.map((m) => ({ ...m.user, handle: `${m.user.username}#${m.user.tag}` })),
      lastMessage: chat.messages[0] ? serializeMessage(chat.messages[0]) : null,
    }));

    return reply.send(chats);
  });

  // POST /api/chats — create or get existing DM
  app.post('/', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ targetUserId: z.string().cuid() });
    const parse = schema.safeParse(request.body);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'targetUserId required', statusCode: 400 });

    const me = (request as any).user;
    const { targetUserId } = parse.data;

    if (me.id === targetUserId) {
      return reply.status(400).send({ error: 'Bad Request', message: 'Cannot chat with yourself', statusCode: 400 });
    }

    // Check if DM already exists between these two users
    const existingChat = await prisma.chat.findFirst({
      where: {
        type: 'DM',
        members: { every: { userId: { in: [me.id, targetUserId] } } },
      },
      include: {
        members: { include: { user: { select: memberSelect } } },
        messages: { orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: memberSelect } } },
      },
    });

    if (existingChat) {
      return reply.send({
        id: existingChat.id,
        type: existingChat.type,
        createdAt: existingChat.createdAt.toISOString(),
        members: existingChat.members.map((m) => ({ ...m.user, handle: `${m.user.username}#${m.user.tag}` })),
        lastMessage: existingChat.messages[0] ? serializeMessage(existingChat.messages[0]) : null,
      });
    }

    // Create new DM
    const chat = await prisma.chat.create({
      data: {
        type: 'DM',
        members: {
          create: [{ userId: me.id }, { userId: targetUserId }],
        },
      },
      include: {
        members: { include: { user: { select: memberSelect } } },
      },
    });

    return reply.status(201).send({
      id: chat.id,
      type: chat.type,
      createdAt: chat.createdAt.toISOString(),
      members: chat.members.map((m) => ({ ...m.user, handle: `${m.user.username}#${m.user.tag}` })),
      lastMessage: null,
    });
  });

  // GET /api/chats/:id/messages
  app.get('/:id/messages', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const { id } = request.params as { id: string };
    const query = request.query as { cursor?: string; limit?: string };
    const limit = Math.min(parseInt(query.limit || '50', 10), 100);

    // Verify membership
    const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId: id, userId: me.id } } });
    if (!member) return reply.status(403).send({ error: 'Forbidden', message: 'Not a member of this chat', statusCode: 403 });

    const messages = await prisma.message.findMany({
      where: { chatId: id, ...(query.cursor ? { createdAt: { lt: new Date(query.cursor) } } : {}) },
      include: {
        sender: { select: memberSelect },
        replyTo: { include: { sender: { select: memberSelect } } },
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
}
