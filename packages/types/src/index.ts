// ─── User ───────────────────────────────────────────────────────────────────
export interface User {
  id: string;
  username: string;
  tag: string;
  avatarUrl: string | null;
  createdAt: string;
}

export interface UserProfile extends User {
  handle: string; // username#tag
}

// ─── Chat ────────────────────────────────────────────────────────────────────
export type ChatType = 'DM' | 'GROUP';

export interface Chat {
  id: string;
  type: ChatType;
  createdAt: string;
  members: User[];
  lastMessage?: Message;
  unreadCount?: number;
}

// ─── Message ─────────────────────────────────────────────────────────────────
export type MessageStatus = 'sent' | 'delivered' | 'seen';

export interface Message {
  id: string;
  chatId: string;
  senderId: string;
  sender: User;
  content: string;
  createdAt: string;
  updatedAt: string;
  status?: MessageStatus;
  replyTo?: Message;
}

// ─── Socket Events ───────────────────────────────────────────────────────────
export interface SendMessagePayload {
  chatId: string;
  content: string;
  replyToId?: string;
}

export interface ReceiveMessagePayload {
  message: Message;
}

export interface TypingPayload {
  chatId: string;
  userId: string;
  username: string;
  isTyping: boolean;
}

export interface PresencePayload {
  userId: string;
  status: 'online' | 'offline';
}

export interface ServerToClientEvents {
  receive_message: (payload: ReceiveMessagePayload) => void;
  typing: (payload: TypingPayload) => void;
  presence: (payload: PresencePayload) => void;
  message_seen: (payload: { messageId: string; chatId: string; userId: string }) => void;
  error: (payload: { message: string }) => void;
}

export interface ClientToServerEvents {
  send_message: (payload: SendMessagePayload) => void;
  typing: (payload: Omit<TypingPayload, 'userId' | 'username'>) => void;
  join_chat: (chatId: string) => void;
  leave_chat: (chatId: string) => void;
  mark_seen: (payload: { chatId: string; messageId: string }) => void;
}

// ─── API Responses ───────────────────────────────────────────────────────────
export interface AuthResponse {
  token: string;
  user: UserProfile;
}

export interface ApiError {
  error: string;
  message: string;
  statusCode: number;
}

export interface PaginatedMessages {
  messages: Message[];
  hasMore: boolean;
  nextCursor?: string;
}
