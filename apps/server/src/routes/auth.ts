import type { FastifyInstance } from 'fastify';
import { OAuth2Client } from 'google-auth-library';
import { prisma } from '../lib/prisma';
import { signJwt } from '../lib/jwt';
import { z } from 'zod';

const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

// Generate a globally unique 7-char ID: 6 digits + 1 capital letter (e.g. 483921A)
function generateTag(): string {
  const digits = String(Math.floor(Math.random() * 900000) + 100000); // 100000–999999
  const letter = String.fromCharCode(65 + Math.floor(Math.random() * 26)); // A–Z
  return digits + letter;
}

async function generateUniqueTag(): Promise<string> {
  for (let i = 0; i < 30; i++) {
    const tag = generateTag();
    const existing = await prisma.user.findUnique({ where: { tag } });
    if (!existing) return tag;
  }
  throw new Error('Could not generate unique tag after 30 attempts');
}

const userSelect = { id: true, username: true, tag: true, avatarUrl: true, createdAt: true };

const fmt = (u: any) => ({
  id: u.id,
  username: u.username ?? null,
  tag: u.tag,
  handle: u.tag,                       // just the 7-char ID — no username#tag
  avatarUrl: u.avatarUrl ?? null,
  createdAt: u.createdAt instanceof Date ? u.createdAt.toISOString() : u.createdAt,
  needsOnboarding: !u.username,
});

export async function authRoutes(app: FastifyInstance) {
  // ── Google OAuth ────────────────────────────────────────────────────────────
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

    let user = await prisma.user.findUnique({ where: { googleId: payload.sub } });

    if (!user) {
      // New user — no username yet (onboarding will set it), just generate unique tag
      const tag = await generateUniqueTag();
      user = await prisma.user.create({
        data: {
          googleId: payload.sub,
          username: null,             // set during onboarding
          tag,
          avatarUrl: payload.picture ?? null,
        },
      });
    } else if (payload.picture && user.avatarUrl !== payload.picture) {
      user = await prisma.user.update({
        where: { id: user.id },
        data: { avatarUrl: payload.picture },
      });
    }

    const token = await signJwt({ sub: user.id, tag: user.tag });

    return reply.send({ token, user: fmt(user) });
  });

  // ── Onboarding — set username after first login ─────────────────────────────
  app.post('/onboard', async (request, reply) => {
    // Validate JWT manually (lightweight — avoids importing full middleware)
    const authHeader = request.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.status(401).send({ error: 'Unauthorized', message: 'Missing token', statusCode: 401 });
    }

    const { verifyJwt } = await import('../lib/jwt');
    let claims: any;
    try { claims = await verifyJwt(authHeader.slice(7)); }
    catch { return reply.status(401).send({ error: 'Unauthorized', message: 'Invalid token', statusCode: 401 }); }

    const schema = z.object({
      username: z.string()
        .min(2, 'Too short — minimum 2 characters')
        .max(30, 'Too long — maximum 30 characters')
        .regex(/^[a-zA-Z0-9_]+$/, 'Only letters, numbers, and underscores allowed'),
    });
    const parse = schema.safeParse(request.body);
    if (!parse.success) {
      return reply.status(400).send({ error: 'Bad Request', message: parse.error.errors[0].message, statusCode: 400 });
    }

    // Check username not already taken
    const taken = await prisma.user.findFirst({
      where: { username: { equals: parse.data.username, mode: 'insensitive' }, NOT: { id: claims.sub } },
    });
    if (taken) {
      return reply.status(409).send({ error: 'Conflict', message: 'Username already taken', statusCode: 409 });
    }

    const user = await prisma.user.update({
      where: { id: claims.sub },
      data: { username: parse.data.username },
      select: userSelect,
    });

    const token = await signJwt({ sub: user.id, tag: user.tag });
    return reply.send({ token, user: fmt(user) });
  });
}
