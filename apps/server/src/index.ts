import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { Server } from 'socket.io';
import { prisma } from './lib/prisma';
import { registerSocketServer } from './socket/index';
import { authRoutes } from './routes/auth';
import { userRoutes } from './routes/users';
import { chatRoutes } from './routes/chats';

async function bootstrap() {
  const app = Fastify({
    logger: {
      level: process.env.NODE_ENV === 'production' ? 'warn' : 'info',
    },
  });

  // ── Plugins ─────────────────────────────────────────────────────────────────
  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(cors, {
    origin: process.env.FRONTEND_URL ? process.env.FRONTEND_URL.split(',') : true,
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

  // ── Socket.IO — attaches to Fastify's internal HTTP server ─────────────────
  const io = new Server(app.server, {
    cors: {
      origin: process.env.FRONTEND_URL ? process.env.FRONTEND_URL.split(',') : '*',
      credentials: true,
    },
    transports: ['websocket', 'polling'],
  });

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
}

bootstrap().catch((err) => {
  console.error(err);
  process.exit(1);
});
