import { useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../lib/api';
import { useChatStore } from '../store/chat.store';
import { getSocket } from './useSocket';

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
  const setMessages = useChatStore((s) => s.setMessages);

  const query = useQuery({
    queryKey: ['messages', chatId],
    queryFn: async () => {
      if (!chatId) return [];
      const res = await api.get(`/chats/${chatId}/messages`);
      return res.data.messages as any[];
    },
    enabled: !!chatId,
  });

  useEffect(() => {
    if (query.data && chatId) setMessages(chatId, query.data);
  }, [query.data, chatId, setMessages]);

  const sendMessage = useCallback(
    (content: string, replyToId?: string) => {
      if (!chatId || !content.trim()) return;
      const socket = getSocket();
      socket?.emit('send_message', { chatId, content: content.trim(), replyToId });
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
