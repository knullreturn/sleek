import { Server, Socket } from 'socket.io';
import { PrismaClient } from '@prisma/client';

export interface AuthSocket extends Socket {
  userId:   string;
  username: string | null;
  tag:      string;
}

export const memberSelect = { id: true, username: true, tag: true, avatarUrl: true } as const;

export const messageInclude = {
  sender:   { select: memberSelect },
  replyTo:  { include: { sender: { select: memberSelect } } },
  pinnedBy: { select: memberSelect },
};

export function serializeMessage(msg: any) {
  return {
    id:              msg.id,
    chatId:          msg.chatId,
    senderId:        msg.senderId,
    sender:          { ...msg.sender, handle: msg.sender.tag },
    content:         msg.content,
    edited:          msg.edited,
    originalContent: msg.originalContent  ?? null,
    deletedAt:       msg.deletedAt        ? msg.deletedAt.toISOString()  : null,
    pinned:          msg.pinned,
    pinnedAt:        msg.pinnedAt         ? msg.pinnedAt.toISOString()   : null,
    pinnedById:      msg.pinnedById       ?? null,
    pinnedBy:        msg.pinnedBy ? { ...msg.pinnedBy, handle: msg.pinnedBy.tag } : null,
    replyTo: msg.replyTo ? {
      id:        msg.replyTo.id,
      content:   msg.replyTo.content,
      deletedAt: msg.replyTo.deletedAt ? msg.replyTo.deletedAt.toISOString() : null,
      sender:    { ...msg.replyTo.sender, handle: msg.replyTo.sender.tag },
    } : null,
    createdAt: msg.createdAt.toISOString(),
    updatedAt: msg.updatedAt.toISOString(),
  };
}
