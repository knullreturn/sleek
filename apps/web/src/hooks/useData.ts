import { useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../lib/api';
import { useChatStore } from '../store/chat.store';
import { safeEmit, getSocket } from './useSocket';

export function useChats() {
  // Use selector to get only what we need — avoids subscribing to entire store
  const setChats = useChatStore((s) => s.setChats);

  const query = useQuery({
    queryKey: ['chats'],
    queryFn: async () => {
      const res = await api.get('/chats');
      return res.data as any[];
    },
  });

  useEffect(() => {
    if (query.data) setChats(query.data);
  }, [query.data, setChats]);

  return query;
}

export function useMessages(chatId: string | null) {
  const mergeMessages = useChatStore((s) => s.mergeMessages);

  const query = useQuery({
    queryKey: ['messages', chatId],
    queryFn: async () => {
      if (!chatId) return [];
      const res = await api.get(`/chats/${chatId}/messages`);
      return res.data.messages as any[];
    },
    enabled: !!chatId,
    staleTime: Infinity,   // socket handles real-time — never auto-refetch
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    // Fix: use mergeMessages instead of setMessages — HTTP response can no longer
    // overwrite socket messages that arrived before the fetch completed.
    if (query.data && chatId) mergeMessages(chatId, query.data);
  }, [query.data, chatId, mergeMessages]);

  const sendMessage = useCallback(
    (content: string, replyToId?: string): boolean => {
      if (!chatId || !content.trim()) return false;
      // Fix: safeEmit returns false immediately if socket is disconnected.
      // The caller (ChatWindow) can show a failed-to-send indicator.
      return safeEmit(
        'send_message',
        { chatId, content: content.trim(), replyToId },
        (ack: any) => {
          if (!ack?.ok) {
            console.error('send_message failed:', ack?.error);
            // TODO: surface error toast when toast system is added
          }
        }
      );
    },
    [chatId]
  );

  const sendTyping = useCallback(
    (isTyping: boolean) => {
      if (!chatId) return;
      const socket = getSocket();
      socket?.emit('typing', { chatId, isTyping });
    },
    [chatId]
  );

  return { ...query, sendMessage, sendTyping };
}
