# SLEEK

A modern, ultra-clean, high-performance chat platform.

## Stack

- **Frontend**: React 18 + TypeScript + Tailwind CSS v4 + Vite
- **Backend**: Fastify + Socket.IO + Prisma (PostgreSQL) + Redis
- **Auth**: Google OAuth → JWT
- **Deploy**: Render (backend + frontend)

## Structure

```
apps/
  web/      # React frontend
  server/   # Fastify backend
packages/
  types/    # Shared TypeScript types
```

## Getting Started

### Prerequisites
- Node.js 20+
- pnpm 9+
- PostgreSQL database (Railway / Render)

### Setup

```bash
# Install all dependencies
pnpm install

# Backend — copy and fill env vars
cp apps/server/.env.example apps/server/.env

# Frontend — copy and fill env vars
cp apps/web/.env.example apps/web/.env.local

# Run DB migrations
cd apps/server && pnpm db:push

# Dev (both apps)
pnpm dev
```

## Deploy

Push to GitHub and connect to Render using the included `render.yaml`.

Set these environment variables on Render:
- `DATABASE_URL` — PostgreSQL connection string
- `GOOGLE_CLIENT_ID` — Google OAuth client ID
- `JWT_SECRET` — Random secret string
- `FRONTEND_URL` — Deployed frontend URL
- `VITE_API_URL` — Deployed backend URL + `/api`
- `VITE_GOOGLE_CLIENT_ID` — Google OAuth client ID
