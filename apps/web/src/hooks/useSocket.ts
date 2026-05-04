import { useEffect, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';

let socket: Socket | null = null;

export function useSocket() {
  const token = useAuthStore((s) => s.token);
  const { addMessage, updateMessage, setTyping, setUserOnline, setSeenUpTo, activeChatId } = useChatStore();
  const activeChatRef = useRef(activeChatId);
  activeChatRef.current = activeChatId;

  useEffect(() => {
    if (!token) return;

    const API_URL = import.meta.env.VITE_API_URL?.replace('/api', '') || '';

    socket = io(API_URL, {
      auth: { token },
      transports: ['websocket', 'polling'],
      reconnectionAttempts: 5,
    });

    socket.on('connect', () => {
      console.log('🔌 Socket connected');
      if (activeChatRef.current) {
        socket?.emit('join_chat', activeChatRef.current);
      }
    });

    socket.on('receive_message', ({ message }: { message: any }) => {
      const myId  = useAuthStore.getState().user?.id;
      const state = useChatStore.getState();

      // Always add to message store
      state.addMessage(message);

      // Update last message + re-sort chat list (if chat already known)
      const chat = state.chats.find((c) => c.id === message.chatId);
      if (chat) {
        useChatStore.getState().upsertChat({ ...chat, lastMessage: message });
      }
      // If chat not in list yet, the server will emit new_chat — handled below

      // Peer replied → clear “seen” green on our previous messages
      if (message.senderId !== myId) {
        state.setSeenUpTo(message.chatId, null);
      }
    });

    // Brand-new chat from someone — server emits this when a socket is joined
    // to a room it wasn't in before (first message from a new person)
    socket.on('new_chat', ({ chat }: { chat: any }) => {
      useChatStore.getState().upsertChat(chat);
    });

    socket.on('message_edited', ({ message }: { message: any }) => {
      updateMessage(message.chatId, {
        id:              message.id,
        content:         message.content,
        edited:          message.edited,
        originalContent: message.originalContent,
        updatedAt:       message.updatedAt,
      });
    });

    socket.on('message_pinned', ({ message }: { message: any }) => {
      updateMessage(message.chatId, { id: message.id, pinned: true, pinnedAt: message.pinnedAt, pinnedBy: message.pinnedBy, pinnedById: message.pinnedById });
    });

    socket.on('message_unpinned', ({ message }: { message: any }) => {
      updateMessage(message.chatId, { id: message.id, pinned: false, pinnedAt: null, pinnedBy: null, pinnedById: null });
    });

    socket.on('message_deleted', ({ message }: { message: any }) => {
      updateMessage(message.chatId, { id: message.id, deletedAt: message.deletedAt, pinned: false, pinnedAt: null, pinnedBy: null });
    });

    // Read receipt: peer has seen our message up to messageId
    socket.on('message_seen', ({ messageId, chatId, userId }: { messageId: string; chatId: string; userId: string }) => {
      const myId = useAuthStore.getState().user?.id;
      // Only react to other users' seen events, not our own echo
      if (userId !== myId) {
        setSeenUpTo(chatId, messageId);
      }
    });

    socket.on('typing', (payload: { chatId: string; userId: string; username: string; isTyping: boolean }) => {
      setTyping(payload.chatId, payload.userId, payload.username, payload.isTyping);
    });

    socket.on('presence', ({ userId, status }: { userId: string; status: 'online' | 'offline' }) => {
      setUserOnline(userId, status === 'online');
    });

    socket.on('connect_error', (err) => {
      console.error('Socket error:', err.message);
    });

    return () => {
      socket?.disconnect();
      socket = null;
    };
  }, [token]);

  return { socket };
}

export function getSocket() {
  return socket;
}
