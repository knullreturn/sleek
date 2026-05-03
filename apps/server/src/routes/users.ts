import type { FastifyInstance } from 'fastify';
import { prisma } from '../lib/prisma';
import { authenticate } from '../lib/auth.middleware';
import { z } from 'zod';

const userSelect = { id: true, username: true, tag: true, avatarUrl: true, createdAt: true };
const fmt = (u: any) => ({
  id: u.id,
  username: u.username ?? null,
  tag: u.tag,
  handle: u.tag,                       // just the 7-char ID
  avatarUrl: u.avatarUrl ?? null,
  createdAt: u.createdAt instanceof Date ? u.createdAt.toISOString() : u.createdAt,
  needsOnboarding: !u.username,
});

export async function userRoutes(app: FastifyInstance) {
  app.get('/me', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const user = await prisma.user.findUnique({ where: { id: me.id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });

  // Search by username (existing users only — for adding new DMs)
  app.get('/search', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ q: z.string().min(1).max(50) });
    const parse = schema.safeParse(request.query);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'q is required', statusCode: 400 });

    const { q } = parse.data;
    const me = (request as any).user;

    // Search by 7-char tag OR by username
    const users = await prisma.user.findMany({
      where: {
        NOT: { id: me.id },
        username: { not: null },        // only fully onboarded users
        OR: [
          { tag: { equals: q.toUpperCase() } },
          { username: { contains: q, mode: 'insensitive' } },
        ],
      },
      select: userSelect,
      take: 10,
      orderBy: { username: 'asc' },
    });

    return reply.send(users.map(fmt));
  });

  app.get('/:id', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const user = await prisma.user.findUnique({ where: { id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });
}
