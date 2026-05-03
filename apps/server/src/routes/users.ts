import type { FastifyInstance } from 'fastify';
import { prisma } from '../lib/prisma';
import { authenticate } from '../lib/auth.middleware';
import { createHash } from 'crypto';
import { z } from 'zod';

const userSelect = { id: true, username: true, tag: true, avatarUrl: true, createdAt: true };
const fmt = (u: any) => ({
  id: u.id, username: u.username ?? null, tag: u.tag,
  handle: u.tag, avatarUrl: u.avatarUrl ?? null,
  createdAt: u.createdAt instanceof Date ? u.createdAt.toISOString() : u.createdAt,
  needsOnboarding: !u.username,
});

export async function userRoutes(app: FastifyInstance) {
  // GET /api/users/me
  app.get('/me', { preHandler: authenticate }, async (request, reply) => {
    const me = (request as any).user;
    const user = await prisma.user.findUnique({ where: { id: me.id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });

  // GET /api/users/me/avatar/sign — generate Cloudinary signed upload params
  app.get('/me/avatar/sign', { preHandler: authenticate }, async (_request, reply) => {
    const cloudName = process.env.CLOUDINARY_CLOUD_NAME;
    const apiKey    = process.env.CLOUDINARY_API_KEY;
    const apiSecret = process.env.CLOUDINARY_API_SECRET;

    if (!cloudName || !apiKey || !apiSecret) {
      return reply.status(503).send({ error: 'Service Unavailable', message: 'Cloudinary not configured', statusCode: 503 });
    }

    const timestamp = Math.floor(Date.now() / 1000).toString();
    const folder    = 'sleek-avatars';

    // Signature = SHA256("folder={folder}&timestamp={timestamp}{secret}")
    const paramsStr = `folder=${folder}&timestamp=${timestamp}`;
    const signature = createHash('sha256').update(paramsStr + apiSecret).digest('hex');

    return reply.send({ signature, timestamp, apiKey, cloudName, folder });
  });

  // PATCH /api/users/me/avatar — save Cloudinary URL to DB
  app.patch('/me/avatar', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ avatarUrl: z.string().url() });
    const parse = schema.safeParse(request.body);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'Invalid avatarUrl', statusCode: 400 });

    const me = (request as any).user;
    const user = await prisma.user.update({
      where: { id: me.id },
      data: { avatarUrl: parse.data.avatarUrl },
      select: userSelect,
    });
    return reply.send(fmt(user));
  });

  // GET /api/users/search
  app.get('/search', { preHandler: authenticate }, async (request, reply) => {
    const schema = z.object({ q: z.string().min(1).max(50) });
    const parse = schema.safeParse(request.query);
    if (!parse.success) return reply.status(400).send({ error: 'Bad Request', message: 'q is required', statusCode: 400 });

    const { q } = parse.data;
    const me = (request as any).user;

    const users = await prisma.user.findMany({
      where: {
        NOT: { id: me.id },
        username: { not: null },
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

  // GET /api/users/:id
  app.get('/:id', { preHandler: authenticate }, async (request, reply) => {
    const { id } = request.params as { id: string };
    const user = await prisma.user.findUnique({ where: { id }, select: userSelect });
    if (!user) return reply.status(404).send({ error: 'Not Found', message: 'User not found', statusCode: 404 });
    return reply.send(fmt(user));
  });
}
