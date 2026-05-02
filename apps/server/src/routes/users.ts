import type { FastifyInstance } from 'fastify';
import { prisma } from '../lib/prisma';
import { authenticate } from '../lib/auth.middleware';
import { z } from 'zod';

const userSelect = { id: true, username: true, tag: true, avatarUrl: true, createdAt: true };
const fmt = (u: any) => ({ ...u, handle: `${u.username}#${u.tag}`, createdAt: u.createdAt.toISOString() });

export async function userRoutes(app: FastifyInstance) {
  app.get('/me', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const user = await prisma.user.findUnique({ where: { id: me.id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });

  app.get('/search', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ q: z.string().min(1).max(50) });
    const parse = schema.safeParse(request.query);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'q is required', statusCode: 400 });

    const { q } = parse.data;
    const me = (request as any).user;

    let users;
    if (q.includes('#')) {
      const [username, tag] = q.split('#');
      users = await prisma.user.findMany({
        where: { username: { equals: username, mode: 'insensitive' }, tag, NOT: { id: me.id } },
        select: userSelect, take: 10,
      });
    } else {
      users = await prisma.user.findMany({
        where: { username: { contains: q, mode: 'insensitive' }, NOT: { id: me.id } },
        select: userSelect, take: 10, orderBy: { username: 'asc' },
      });
    }

    return reply.send(users.map(fmt));
  });

  app.get('/:id', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const user = await prisma.user.findUnique({ where: { id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });
}
