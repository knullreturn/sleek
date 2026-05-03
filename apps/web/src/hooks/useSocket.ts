import { useEffect, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';

let socket: Socket | null = null;

export function useSocket() {
  const token = useAuthStore((s) => s.token);
  const { addMessage, updateMessage, setTyping, setUserOnline, activeChatId } = useChatStore();
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
      // Join active chat if any
      if (activeChatRef.current) {
        socket?.emit('join_chat', activeChatRef.current);
      }
    });

    socket.on('receive_message', ({ message }: { message: any }) => {
      addMessage(message);
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
