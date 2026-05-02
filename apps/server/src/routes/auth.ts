import type { FastifyInstance } from 'fastify';
import { OAuth2Client } from 'google-auth-library';
import { prisma } from '../lib/prisma';
import { signJwt } from '../lib/jwt';
import { z } from 'zod';

const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

async function generateUniqueTag(username: string): Promise<string> {
  let attempts = 0;
  while (attempts < 20) {
    const tag = String(Math.floor(Math.random() * 90000) + 10000);
    const existing = await prisma.user.findUnique({ where: { username_tag: { username, tag } } });
    if (!existing) return tag;
    attempts++;
  }
  throw new Error('Unable to generate unique tag');
}

export async function authRoutes(app: FastifyInstance) {
  app.post('/google', async (request, reply) => {
    const schema = z.object({ idToken: z.string().min(1) });
    const parse = schema.safeParse(request.body);
    if (!parse.success) {
      return reply.status(400).send({ error: 'Bad Request', message: 'idToken is required', statusCode: 400 });
    }

    let ticket;
    try {
      ticket = await googleClient.verifyIdToken({
        idToken: parse.data.idToken,
        audience: process.env.GOOGLE_CLIENT_ID,
      });
    } catch {
      return reply.status(401).send({ error: 'Unauthorized', message: 'Invalid Google token', statusCode: 401 });
    }

    const payload = ticket.getPayload();
    if (!payload?.sub) {
      return reply.status(401).send({ error: 'Unauthorized', message: 'Could not extract Google user', statusCode: 401 });
    }

    const rawUsername = (payload.given_name || payload.email?.split('@')[0] || 'user')
      .toLowerCase()
      .replace(/[^a-z0-9_]/g, '')
      .slice(0, 20) || 'user';

    let user = await prisma.user.findUnique({ where: { googleId: payload.sub } });

    if (!user) {
      const tag = await generateUniqueTag(rawUsername);
      user = await prisma.user.create({
        data: { googleId: payload.sub, username: rawUsername, tag, avatarUrl: payload.picture || null },
      });
    } else if (payload.picture && user.avatarUrl !== payload.picture) {
      user = await prisma.user.update({
        where: { id: user.id },
        data: { avatarUrl: payload.picture },
      });
    }

    const token = await signJwt({ sub: user.id, username: user.username, tag: user.tag });

    return reply.send({
      token,
      user: {
        id: user.id,
        username: user.username,
        tag: user.tag,
        avatarUrl: user.avatarUrl,
        handle: `${user.username}#${user.tag}`,
        createdAt: user.createdAt.toISOString(),
      },
    });
  });
}
