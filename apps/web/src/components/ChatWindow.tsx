import React, { useEffect, useRef, useState } from 'react';
import { Avatar } from './Avatar';
import { TypingIndicator } from './TypingIndicator';
import { formatMessageTime, formatDateSeparator, isSameDay, getDmPeer } from '../lib/utils';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';
import { useMessages } from '../hooks/useData';
import { getSocket } from '../hooks/useSocket';
import { Send, Paperclip, Smile } from 'lucide-react';

// Stable references — prevent new array on every render (causes infinite loop)
const EMPTY_MESSAGES: any[] = [];
const EMPTY_TYPING: any[] = [];

interface MessageBubbleProps {
  message: any;
  isOwn: boolean;
  showAvatar: boolean;
  showSender: boolean;
}

function MessageBubble({ message, isOwn, showAvatar, showSender }: MessageBubbleProps) {
  return (
    <div className={`msg-row ${isOwn ? 'own' : ''}`}>
      {!isOwn && (
        <div style={{ width: 32, flexShrink: 0 }}>
          {showAvatar && (
            <Avatar
              src={message.sender?.avatarUrl}
              username={message.sender?.username || '?'}
              size="sm"
            />
          )}
        </div>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: isOwn ? 'flex-end' : 'flex-start', gap: 2, flex: 1, minWidth: 0 }}>
        {showSender && !isOwn && (
          <span className="msg-sender-name">{message.sender?.username}</span>
        )}
        {message.replyTo && (
          <div className="reply-preview">
            <div className="reply-preview-sender">{message.replyTo.sender?.username}</div>
            <div className="reply-preview-content" style={{ lineClamp: 1 }}>
              {message.replyTo.content}
            </div>
          </div>
        )}
        <div className={`msg-bubble ${isOwn ? 'own' : 'other'}`}>
          <span style={{ wordBreak: 'break-word', lineHeight: 1.55 }}>{message.content}</span>
          <span
            style={{
              float: 'right',
              fontSize: 10,
              opacity: 0.55,
              marginLeft: 8,
              marginTop: 4,
              position: 'relative',
              top: 3,
              whiteSpace: 'nowrap',
              letterSpacing: 0.2,
            }}
          >
            {formatMessageTime(message.createdAt)}
          </span>
        </div>
      </div>
    </div>
  );
}

// Skeleton bubble for loading state
function MessageSkeleton({ own, width }: { own?: boolean; width: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '4px 0', flexDirection: own ? 'row-reverse' : 'row' }}>
      {!own && <div className="skeleton" style={{ width: 32, height: 32, borderRadius: '50%', flexShrink: 0 }} />}
      <div className="skeleton" style={{ height: 38, width, borderRadius: 16 }} />
    </div>
  );
}

export function ChatWindow({ chatId }: { chatId: string }) {
  const user = useAuthStore((s) => s.user);
  const messages = useChatStore((s) => s.messages[chatId] ?? EMPTY_MESSAGES);
  const typingMap = useChatStore((s) => s.typing[chatId] ?? EMPTY_TYPING);
  const chats = useChatStore((s) => s.chats);
  const { sendMessage, sendTyping, isLoading } = useMessages(chatId);

  // Get peer avatar for the typing indicator
  const activeChat = chats.find((c) => c.id === chatId);
  const peer = activeChat ? getDmPeer(activeChat, user?.id || '') : null;

  const typingNames = typingMap
    .filter((t) => t.userId !== user?.id)
    .map((t) => t.username);

  const [input, setInput] = useState('');
  const [typingTimeout, setTypingTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Join chat room on mount
  useEffect(() => {
    const socket = getSocket();
    socket?.emit('join_chat', chatId);
    return () => { socket?.emit('leave_chat', chatId); };
  }, [chatId]);

  // Scroll to bottom on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Also scroll when typing indicator appears so it's never half-cut
  useEffect(() => {
    if (typingNames.length > 0) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [typingNames]);

  const handleSend = () => {
    if (!input.trim()) return;
    sendMessage(input);
    setInput('');
    textareaRef.current?.focus();
    // stop typing
    sendTyping(false);
    if (typingTimeout) clearTimeout(typingTimeout);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);

    // Auto-resize
    const ta = e.target;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(ta.scrollHeight, 160)}px`;

    // Typing indicator
    sendTyping(true);
    if (typingTimeout) clearTimeout(typingTimeout);
    setTypingTimeout(setTimeout(() => sendTyping(false), 2000));
  };

  // Group messages by day and consecutive sender
  const grouped: Array<{ date: string; messages: any[] }> = [];
  messages.forEach((msg) => {
    const lastGroup = grouped[grouped.length - 1];
    if (!lastGroup || !isSameDay(lastGroup.date, msg.createdAt)) {
      grouped.push({ date: msg.createdAt, messages: [msg] });
    } else {
      lastGroup.messages.push(msg);
    }
  });

  const typingNames = typingMap
    .filter((t) => t.userId !== user?.id)
    .map((t) => t.username);

  return (
    <>
      <div className="chat-canvas" id="chat-canvas">

        {/* Loading skeletons */}
        {isLoading && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '8px 0' }}>
            <MessageSkeleton width="45%" />
            <MessageSkeleton own width="55%" />
            <MessageSkeleton width="30%" />
            <MessageSkeleton own width="65%" />
            <MessageSkeleton width="50%" />
            <MessageSkeleton own width="40%" />
          </div>
        )}

        {!isLoading && messages.length === 0 && (
          <div className="empty-state" style={{ flex: 1, paddingTop: 80 }}>
            <div className="empty-state-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <h3>Start the conversation</h3>
            <p>Send a message to kick things off</p>
          </div>
        )}

        {grouped.map(({ date, messages: dayMsgs }) => (
          <React.Fragment key={date}>
            <div className="date-separator">{formatDateSeparator(date)}</div>
            {dayMsgs.map((msg, i) => {
              const isOwn = msg.senderId === user?.id;
              const prev = dayMsgs[i - 1];
              const showAvatar = !prev || prev.senderId !== msg.senderId;
              const showSender = !prev || prev.senderId !== msg.senderId;
              return (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  isOwn={isOwn}
                  showAvatar={showAvatar}
                  showSender={showSender}
                />
              );
            })}
          </React.Fragment>
        ))}

        {/* Typing indicator — shown as a message in chat area */}
        {typingNames.length > 0 && (
          <TypingIndicator names={typingNames} avatarUrl={peer?.avatarUrl} avatarUsername={peer?.username} />
        )}

        <div ref={bottomRef} />
      </div>

      <div className="chat-input-bar">
        <button className="icon-btn" title="Attach file" aria-label="Attach file">
          <Paperclip size={18} />
        </button>

        <textarea
          ref={textareaRef}
          id="message-input"
          className="input-field"
          placeholder="Message…"
          value={input}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          rows={1}
          style={{ height: 40 }}
          aria-label="Message input"
        />

        <button className="icon-btn" title="Emoji" aria-label="Emoji">
          <Smile size={18} />
        </button>

        <button
          id="send-btn"
          className="send-btn"
          onClick={handleSend}
          disabled={!input.trim()}
          title="Send"
          aria-label="Send message"
        >
          <Send size={16} />
        </button>
      </div>

    </>
  );
}
