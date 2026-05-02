import type { FastifyInstance } from 'fastify';
import { PrismaClient } from '@prisma/client';
import { authenticate } from '../lib/auth.middleware.js';
import { z } from 'zod';

const prisma = new PrismaClient();

const userSelect = {
  id: true,
  username: true,
  tag: true,
  avatarUrl: true,
  createdAt: true,
};

export async function userRoutes(app: FastifyInstance) {
  // GET /api/users/me
  app.get('/me', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const user = await prisma.user.findUnique({ where: { id: me.id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send({ ...user, handle: `${user.username}#${user.tag}`, createdAt: user.createdAt.toISOString() });
  });

  // GET /api/users/search?q=username%231234  OR  ?q=username
  app.get('/search', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ q: z.string().min(1).max(50) });
    const parse = schema.safeParse(request.query);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'q is required', statusCode: 400 });

    const { q } = parse.data;
    const me = (request as any).user;

    let users;
    // Check if searching by full handle (username#tag)
    if (q.includes('#')) {
      const [username, tag] = q.split('#');
      users = await prisma.user.findMany({
        where: {
          username: { equals: username, mode: 'insensitive' },
          tag,
          NOT: { id: me.id },
        },
        select: userSelect,
        take: 10,
      });
    } else {
      users = await prisma.user.findMany({
        where: {
          username: { contains: q, mode: 'insensitive' },
          NOT: { id: me.id },
        },
        select: userSelect,
        take: 10,
        orderBy: { username: 'asc' },
      });
    }

    return reply.send(
      users.map((u) => ({ ...u, handle: `${u.username}#${u.tag}`, createdAt: u.createdAt.toISOString() }))
    );
  });

  // GET /api/users/:id
  app.get('/:id', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const user = await prisma.user.findUnique({ where: { id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send({ ...user, handle: `${user.username}#${user.tag}`, createdAt: user.createdAt.toISOString() });
  });
}
