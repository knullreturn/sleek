import { useEffect, useRef } from 'react';
import { io, Socket } from 'socket.io-client';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';
import { useUIStore } from '../store/ui.store';

let socket: Socket | null = null;

export function useSocket() {
  const token = useAuthStore((s) => s.token);
  const { addMessage, updateMessage, setTyping, setUserOnline, setSeenUpTo, activeChatId } = useChatStore();
  const activeChatRef = useRef(activeChatId);
  activeChatRef.current = activeChatId;

  // Keep a ref to the token so reconnect handlers can access the latest value
  const tokenRef = useRef(token);
  tokenRef.current = token;

  useEffect(() => {
    if (!token) return;

    const API_URL = import.meta.env.VITE_API_URL?.replace('/api', '') || '';

    socket = io(API_URL, {
      auth: { token },
      transports: ['websocket', 'polling'],
      reconnectionAttempts: Infinity,
      reconnectionDelay:    1000,
      reconnectionDelayMax: 30000,
    });

    socket.on('connect', () => {
      console.log('🔌 Socket connected');
      if (activeChatRef.current) {
        socket?.emit('join_chat', activeChatRef.current);
      }
      // Re-sync sleep mode — server's in-memory state resets on reconnect,
      // so we push the locally-persisted preference on every connect.
      const sleeping = localStorage.getItem('sleek-sleep-mode') === 'true';
      if (sleeping) {
        socket?.emit('set_sleep_mode', { enabled: true });
      }
    });

    socket.on('disconnect', (reason) => {
      console.warn('⚠️ Socket disconnected:', reason);
    });

    socket.on('receive_message', ({ message }: { message: any }) => {
      const myId  = useAuthStore.getState().user?.id;
      const state = useChatStore.getState();

      state.addMessage(message);

      const chat = state.chats.find((c) => c.id === message.chatId);
      if (chat) {
        useChatStore.getState().upsertChat({ ...chat, lastMessage: message });
      }

      if (message.senderId !== myId && message.chatId !== activeChatRef.current) {
        state.incrementUnread(message.chatId);
      }

      if (message.senderId !== myId) {
        state.setSeenUpTo(message.chatId, null);
      }
    });

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

    socket.on('message_seen', ({ messageId, chatId, userId }: { messageId: string; chatId: string; userId: string }) => {
      const myId = useAuthStore.getState().user?.id;
      if (userId !== myId) {
        setSeenUpTo(chatId, messageId);
      }
    });

    socket.on('typing', (payload: { chatId: string; userId: string; username: string; isTyping: boolean }) => {
      setTyping(payload.chatId, payload.userId, payload.username, payload.isTyping);
    });

    socket.on('presence', ({ userId, status }: { userId: string; status: 'online' | 'offline' | 'sleeping' }) => {
      // 'sleeping' counts as online (reachable) but with DND — pass both flags
      setUserOnline(userId, status === 'online' || status === 'sleeping', status === 'sleeping');
    });

    // Fix: consume presence_snapshot with sleep state
    socket.on('presence_snapshot', ({ onlineUserIds, sleepingUserIds = [] }: { onlineUserIds: string[]; sleepingUserIds?: string[] }) => {
      const store = useChatStore.getState();
      for (const uid of onlineUserIds) {
        store.setUserOnline(uid, true, sleepingUserIds.includes(uid));
      }
    });

    socket.on('connect_error', (err) => {
      console.error('Socket error:', err.message);
    });

    // ── Fix: reconnect when browser tab becomes visible or network comes back ─
    // Without this, if the socket died while the tab was hidden (phone locked,
    // tab in background), it may never recover after the tab is shown again.
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && socket && !socket.connected) {
        console.log('🔄 Tab visible — forcing socket reconnect');
        socket.connect();
      }
    };
    const handleOnline = () => {
      if (socket && !socket.connected) {
        console.log('🔄 Network online — forcing socket reconnect');
        socket.connect();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('online', handleOnline);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('online', handleOnline);
      socket?.disconnect();
      socket = null;
    };
  }, [token]);

  return { socket };
}

export function getSocket() {
  return socket;
}

/**
 * Fix: safe emit — returns false if socket is not connected.
 * Use instead of getSocket()?.emit() for sends that must not silently drop.
 */
export function safeEmit(event: string, data: any, ack?: (res: any) => void): boolean {
  if (!socket?.connected) return false;
  if (ack) {
    socket.emit(event, data, ack);
  } else {
    socket.emit(event, data);
  }
  return true;
}
