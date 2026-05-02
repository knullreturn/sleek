import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { PrismaClient } from '@prisma/client';
import { Server } from 'socket.io';
import { createServer } from 'http';
import { registerSocketServer } from './socket/index.js';
import { authRoutes } from './routes/auth.js';
import { userRoutes } from './routes/users.js';
import { chatRoutes } from './routes/chats.js';

const prisma = new PrismaClient();

async function bootstrap() {
  const app = Fastify({ logger: true });
  const httpServer = createServer(app.server as any);

  // ── Plugins ────────────────────────────────────────────────────────────────
  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(cors, {
    origin: process.env.FRONTEND_URL || '*',
    credentials: true,
  });

  // ── Decorate with prisma ───────────────────────────────────────────────────
  app.decorate('prisma', prisma);

  // ── Routes ─────────────────────────────────────────────────────────────────
  await app.register(authRoutes, { prefix: '/api/auth' });
  await app.register(userRoutes, { prefix: '/api/users' });
  await app.register(chatRoutes, { prefix: '/api/chats' });

  // ── Health check ───────────────────────────────────────────────────────────
  app.get('/health', async () => ({ status: 'ok', timestamp: new Date().toISOString() }));

  // ── Socket.IO ──────────────────────────────────────────────────────────────
  const io = new Server(httpServer, {
    cors: {
      origin: process.env.FRONTEND_URL || '*',
      credentials: true,
    },
    transports: ['websocket', 'polling'],
  });

  registerSocketServer(io, prisma);

  // ── Start ──────────────────────────────────────────────────────────────────
  const port = parseInt(process.env.PORT || '3001', 10);

  // We use httpServer (not app.listen) so Socket.IO shares the same port
  await app.ready();
  httpServer.listen(port, '0.0.0.0', () => {
    console.log(`🚀 SLEEK server running on port ${port}`);
  });

  // ── Graceful shutdown ──────────────────────────────────────────────────────
  const shutdown = async () => {
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
