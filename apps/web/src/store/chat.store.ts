import { create } from 'zustand';

interface Chat {
  id: string;
  type: 'DM' | 'GROUP';
  members: any[];
  lastMessage: any;
  createdAt: string;
}

interface Message {
  id:              string;
  chatId:          string;
  senderId:        string;
  sender:          any;
  content:         string;
  edited:          boolean;
  originalContent: string | null;
  deletedAt:       string | null;
  pinned:          boolean;
  pinnedAt:        string | null;
  pinnedById:      string | null;
  pinnedBy:        any | null;
  createdAt:       string;
  updatedAt:       string;
  replyTo?:        any;
}

interface TypingState {
  [chatId: string]: { userId: string; username: string }[];
}

interface ChatState {
  chats: Chat[];
  messages: Record<string, Message[]>;
  activeChatId: string | null;
  typing: TypingState;
  onlineUsers: Set<string>;
  seenUpTo: Record<string, string | null>;
  unreadCounts: Record<string, number>;

  setChats:        (chats: Chat[]) => void;
  upsertChat:      (chat: Chat) => void;
  setActiveChatId: (id: string | null) => void;
  setMessages:     (chatId: string, messages: Message[]) => void;
  prependMessages: (chatId: string, messages: Message[]) => void;
  addMessage:      (message: Message) => void;
  updateMessage:   (chatId: string, patch: Partial<Message> & { id: string }) => void;
  setTyping:       (chatId: string, userId: string, username: string, isTyping: boolean) => void;
  setUserOnline:   (userId: string, online: boolean) => void;
  setSeenUpTo:     (chatId: string, messageId: string | null) => void;
  incrementUnread: (chatId: string) => void;
  clearUnread:     (chatId: string) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  chats: [],
  messages: {},
  activeChatId: null,
  typing: {},
  onlineUsers: new Set(),
  seenUpTo: {},
  unreadCounts: {},

  setChats: (chats) => set({ chats }),

  upsertChat: (chat) =>
    set((state) => {
      const idx = state.chats.findIndex((c) => c.id === chat.id);
      if (idx >= 0) {
        const updated = [...state.chats];
        updated[idx] = chat;
        return { chats: updated };
      }
      return { chats: [chat, ...state.chats] };
    }),

  setActiveChatId: (id) => set((state) => ({
    activeChatId: id,
    // Auto-clear unread when opening a chat
    unreadCounts: id ? { ...state.unreadCounts, [id]: 0 } : state.unreadCounts,
  })),

  setMessages: (chatId, messages) =>
    set((state) => ({ messages: { ...state.messages, [chatId]: messages } })),

  prependMessages: (chatId, messages) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [chatId]: [...messages, ...(state.messages[chatId] || [])],
      },
    })),

  addMessage: (message) =>
    set((state) => {
      const existing = state.messages[message.chatId] || [];
      // Avoid duplicates
      if (existing.some((m) => m.id === message.id)) return state;

      // Update chat's lastMessage
      const chats = state.chats.map((c) =>
        c.id === message.chatId ? { ...c, lastMessage: message } : c
      );
      // Move active chat to top
      const chatIdx = chats.findIndex((c) => c.id === message.chatId);
      if (chatIdx > 0) {
        const [chat] = chats.splice(chatIdx, 1);
        chats.unshift(chat);
      }

      return {
        messages: { ...state.messages, [message.chatId]: [...existing, message] },
        chats,
      };
    }),

  updateMessage: (chatId, patch) =>
    set((state) => {
      const msgs = state.messages[chatId];
      if (!msgs) return state;
      return {
        messages: {
          ...state.messages,
          [chatId]: msgs.map((m) => m.id === patch.id ? { ...m, ...patch } : m),
        },
      };
    }),

  setTyping: (chatId, userId, username, isTyping) =>
    set((state) => {
      const current = state.typing[chatId] || [];
      if (isTyping) {
        if (current.some((t) => t.userId === userId)) return state;
        return { typing: { ...state.typing, [chatId]: [...current, { userId, username }] } };
      } else {
        return {
          typing: { ...state.typing, [chatId]: current.filter((t) => t.userId !== userId) },
        };
      }
    }),

  setUserOnline: (userId, online) =>
    set((state) => {
      const next = new Set(state.onlineUsers);
      if (online) next.add(userId); else next.delete(userId);
      return { onlineUsers: next };
    }),

  setSeenUpTo: (chatId, messageId) =>
    set((state) => ({ seenUpTo: { ...state.seenUpTo, [chatId]: messageId } })),

  incrementUnread: (chatId) =>
    set((state) => ({
      unreadCounts: { ...state.unreadCounts, [chatId]: (state.unreadCounts[chatId] || 0) + 1 },
    })),

  clearUnread: (chatId) =>
    set((state) => ({ unreadCounts: { ...state.unreadCounts, [chatId]: 0 } })),
}));
