import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { Server } from 'socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import { Redis } from 'ioredis';
import { prisma } from './lib/prisma';
import { registerSocketServer } from './socket/index';
import { authRoutes } from './routes/auth';
import { userRoutes } from './routes/users';
import { chatRoutes } from './routes/chats';
import { initFirebaseAdmin } from './lib/firebase';

async function bootstrap() {
  const app = Fastify({
    logger: {
      level: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
    },
  });

  // ── Plugins ─────────────────────────────────────────────────────────────────
  await app.register(helmet, { contentSecurityPolicy: false });
  // ✅ Security: CORS restricted to FRONTEND_URL in production.
  // Falls back to deny-all (false) if FRONTEND_URL is missing — warn loudly but don't crash.
  const corsOrigin = (() => {
    if (process.env.FRONTEND_URL) return process.env.FRONTEND_URL.split(',');
    if (process.env.NODE_ENV === 'production') {
      console.error('[SECURITY] FRONTEND_URL is not set — CORS will deny all cross-origin requests. Set FRONTEND_URL in Railway env vars.');
      return false; // deny all cross-origin requests rather than crash or allow all
    }
    return true; // dev only: allow all origins
  })();

  await app.register(cors, {
    origin:      corsOrigin,
    credentials: true,
  });

  // ── Routes ──────────────────────────────────────────────────────────────────
  await app.register(authRoutes, { prefix: '/api/auth' });
  await app.register(userRoutes, { prefix: '/api/users' });
  await app.register(chatRoutes, { prefix: '/api/chats' });

  // ── Health check ────────────────────────────────────────────────────────────
  app.get('/health', async () => ({ status: 'ok', timestamp: new Date().toISOString() }));

  // Must call ready() before accessing app.server
  await app.ready();

  // ── Socket.IO ───────────────────────────────────────────────────────────────
  const io = new Server(app.server, {
    cors: {
      origin: process.env.FRONTEND_URL ? process.env.FRONTEND_URL.split(',') : '*',
      credentials: true,
    },
    transports: ['websocket', 'polling'],
  });

  // ── Redis adapter (if REDIS_URL is set) ─────────────────────────────────────
  if (process.env.REDIS_URL) {
    try {
      const pubClient = new Redis(process.env.REDIS_URL);
      const subClient = pubClient.duplicate();

      await Promise.all([
        new Promise<void>((res, rej) => pubClient.once('ready', res).once('error', rej)),
        new Promise<void>((res, rej) => subClient.once('ready', res).once('error', rej)),
      ]);

      io.adapter(createAdapter(pubClient, subClient));
      console.log('✅ Redis adapter connected');

      // Clean up on shutdown
      process.on('SIGTERM', () => { pubClient.quit(); subClient.quit(); });
      process.on('SIGINT',  () => { pubClient.quit(); subClient.quit(); });
    } catch (err) {
      console.warn('⚠️  Redis connection failed — running without adapter:', err);
    }
  } else {
    console.log('ℹ️  REDIS_URL not set — using in-memory adapter');
  }

  registerSocketServer(io, prisma);

  // ── Start ───────────────────────────────────────────────────────────────────
  const port = parseInt(process.env.PORT || '3001', 10);
  await app.listen({ port, host: '0.0.0.0' });
  console.log(`🚀 SLEEK server running on port ${port}`);

  // ── Graceful shutdown ───────────────────────────────────────────────────────
  const shutdown = async () => {
    await app.close();
    await prisma.$disconnect();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  // Init Firebase Admin (safe — won't crash if package missing or env not set)
  initFirebaseAdmin().catch(() => {});
}

bootstrap().catch((err) => {
  console.error(err);
  process.exit(1);
});
