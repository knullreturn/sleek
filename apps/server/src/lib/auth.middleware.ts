import { PrismaClient } from '@prisma/client';
import { verifyJwt } from './jwt.js';
import type { FastifyRequest, FastifyReply } from 'fastify';

const prisma = new PrismaClient();

export async function authenticate(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  try {
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Unauthorized', message: 'Missing token', statusCode: 401 });
    }

    const token = authHeader.slice(7);
    const payload = await verifyJwt(token);

    const user = await prisma.user.findUnique({
      where: { id: payload.sub as string },
      select: { id: true, username: true, tag: true, avatarUrl: true },
    });

    if (!user) {
      return reply.status(401).send({ error: 'Unauthorized', message: 'User not found', statusCode: 401 });
    }

    (request as any).user = user;
  } catch {
    return reply.status(401).send({ error: 'Unauthorized', message: 'Invalid token', statusCode: 401 });
  }
}
