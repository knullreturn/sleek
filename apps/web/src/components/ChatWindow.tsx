import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Avatar } from './Avatar';
import { TypingIndicator } from './TypingIndicator';
import { MessageContextMenu } from './MessageContextMenu';
import { formatMessageTime, formatDateSeparator, isSameDay, getDmPeer } from '../lib/utils';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';
import { useMessages } from '../hooks/useData';
import { getSocket } from '../hooks/useSocket';
import { Send, Paperclip, Smile, X, Pin } from 'lucide-react';

// Stable empty references — prevent new array identity on every render
const EMPTY_MESSAGES: any[] = [];
const EMPTY_TYPING:   any[] = [];

// ── Types ─────────────────────────────────────────────────────────────────────
interface MessageBubbleProps {
  message:       any;
  isOwn:         boolean;
  showMeta:      boolean;      // show avatar + sender name
  replyTo:       any | null;
  isEditing:     boolean;
  onContextMenu: (e: React.MouseEvent, message: any) => void;
  onScrollTo:    (id: string) => void;
  onEditSave:    (newContent: string) => void;
  onEditCancel:  () => void;
}

// ── EditInput ─────────────────────────────────────────────────────────────────
// Inline edit textarea — autofocuses, auto-resizes, Enter saves, Escape cancels
function EditInput({ initial, onSave, onCancel }: {
  initial:  string;
  onSave:   (v: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState(initial);
  const taRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const ta = taRef.current;
    if (!ta) return;
    ta.focus();
    ta.selectionStart = ta.selectionEnd = ta.value.length;
    ta.style.height = 'auto';
    ta.style.height = `${ta.scrollHeight}px`;
  }, []);

  const handleKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (value.trim()) onSave(value.trim());
    }
    if (e.key === 'Escape') onCancel();
  };

  return (
    <div className="edit-input-wrap">
      <textarea
        ref={taRef}
        className="edit-textarea"
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          e.target.style.height = 'auto';
          e.target.style.height = `${e.target.scrollHeight}px`;
        }}
        onKeyDown={handleKey}
        rows={1}
      />
      <div className="edit-input-hint">Enter to save · Esc to cancel</div>
    </div>
  );
}

// ── EditedTag ─────────────────────────────────────────────────────────────────
// Press and hold to peek at the original message content; release to return.
function EditedTag({ peeking, onPeekStart, onPeekEnd }: {
  peeking:      boolean;
  onPeekStart:  () => void;
  onPeekEnd:    () => void;
}) {
  return (
    <span className="edited-tag-wrap">
      <button
        className={`edited-tag${peeking ? ' peeking' : ''}`}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); onPeekStart(); }}
        onPointerUp={onPeekEnd}
        onPointerLeave={onPeekEnd}
        onPointerCancel={onPeekEnd}
        onContextMenu={(e) => e.preventDefault()}   // prevent long-press menu on mobile
        title="Hold to see original"
      >
        {peeking ? 'original' : 'edited'}
      </button>
    </span>
  );
}

// ── MessageBubble ─────────────────────────────────────────────────────────────
function MessageBubble({
  message, isOwn, showMeta, replyTo,
  isEditing, onContextMenu, onScrollTo, onEditSave, onEditCancel,
}: MessageBubbleProps) {
  const [peekOriginal, setPeekOriginal] = useState(false);
  const canPeek      = message.edited && !!message.originalContent;
  const displayContent = peekOriginal && canPeek ? message.originalContent! : message.content;

  const isDeleted = !!message.deletedAt;

  return (
    <div id={`msg-${message.id}`} className={`msg-row${isOwn ? ' own' : ''}`}>
      {/* Avatar column (other-side messages only) */}
      {!isOwn && (
        <div style={{ width: 32, flexShrink: 0 }}>
          {showMeta && (
            <Avatar src={message.sender?.avatarUrl} username={message.sender?.username || '?'} size="sm" />
          )}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: isOwn ? 'flex-end' : 'flex-start', gap: 2, flex: 1, minWidth: 0 }}>
        {/* Sender name */}
        {showMeta && !isOwn && (
          <span className="msg-sender-name">{message.sender?.username}</span>
        )}

        {/* Reply chip */}
        {replyTo && <ReplyChip replyTo={replyTo} isOwn={isOwn} onScrollTo={onScrollTo} />}

        {/* ── Bubble variants ── */}
        {isDeleted ? (
          // Tombstone — no context menu, no content
          <div className={`msg-bubble${isOwn ? ' own' : ' other'} msg-bubble-deleted`}>
            <span className="msg-deleted-text">🗑 This message was deleted</span>
            <span className="msg-meta-time" style={{ opacity: 0.4 }}>
              {formatMessageTime(message.createdAt)}
            </span>
          </div>
        ) : isEditing ? (
          <EditInput initial={message.content} onSave={onEditSave} onCancel={onEditCancel} />
        ) : (
          <div
            className={`msg-bubble${isOwn ? ' own' : ' other'}${peekOriginal ? ' peeking-bubble' : ''}`}
            onContextMenu={(e) => { e.preventDefault(); onContextMenu(e, message); }}
          >
            {/* Pin badge */}
            {message.pinned && (
              <span className="msg-pin-badge" title="Pinned message">
                <Pin size={10} />
              </span>
            )}

            {/* Message text — key forces remount to re-trigger fade on swap */}
            <span
              key={peekOriginal ? 'orig' : 'curr'}
              className={`msg-content-text${peekOriginal ? ' content-peek' : ' content-current'}`}
              style={{ wordBreak: 'break-word', lineHeight: 1.55, display: 'block' }}
            >
              {displayContent}
            </span>

            {/* Meta row: edited tag + timestamp */}
            <span className="msg-meta-row">
              {canPeek && (
                <EditedTag
                  peeking={peekOriginal}
                  onPeekStart={() => setPeekOriginal(true)}
                  onPeekEnd={() => setPeekOriginal(false)}
                />
              )}
              <span className="msg-meta-time">{formatMessageTime(message.createdAt)}</span>
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

// ── ReplyChip ─────────────────────────────────────────────────────────────────
// Floating chip above a bubble showing what message it replies to.
function ReplyChip({ replyTo, isOwn, onScrollTo }: {
  replyTo:    any;
  isOwn:      boolean;
  onScrollTo: (id: string) => void;
}) {
  const isDeleted = !!replyTo.deletedAt;

  return (
    <div className={`reply-chip-wrap${isOwn ? ' own' : ''}`}>
      <button
        className="reply-chip"
        onClick={() => !isDeleted && onScrollTo(replyTo.id)}
        title={isDeleted ? 'Original message was deleted' : 'Jump to original message'}
        style={isDeleted ? { cursor: 'default', opacity: 0.5 } : {}}
      >
        <Avatar
          src={replyTo.sender?.avatarUrl}
          username={replyTo.sender?.username || '?'}
          size="xs"
        />
        <span className="reply-chip-name">{replyTo.sender?.username}</span>
        <span className="reply-chip-dot">·</span>
        <span className="reply-chip-text">
          {isDeleted ? 'Message deleted' : replyTo.content}
        </span>
      </button>
      <div className="reply-chip-line" />
    </div>
  );
}

// ── ReplyBar ──────────────────────────────────────────────────────────────────
// Bar shown above the input when the user is composing a reply.
function ReplyBar({ replyTo, onCancel }: { replyTo: any; onCancel: () => void }) {
  return (
    <div className="reply-bar">
      <div className="reply-bar-accent" />
      <div className="reply-bar-body">
        <span className="reply-bar-label">
          Replying to <strong>{replyTo.sender?.username}</strong>
        </span>
        <span className="reply-bar-preview">{replyTo.content}</span>
      </div>
      <button className="icon-btn" onClick={onCancel} title="Cancel reply" aria-label="Cancel reply" style={{ flexShrink: 0 }}>
        <X size={14} />
      </button>
    </div>
  );
}

// ── MessageSkeleton ───────────────────────────────────────────────────────────
function MessageSkeleton({ own, width }: { own?: boolean; width: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '4px 0', flexDirection: own ? 'row-reverse' : 'row' }}>
      {!own && <div className="skeleton" style={{ width: 32, height: 32, borderRadius: '50%', flexShrink: 0 }} />}
      <div className="skeleton" style={{ height: 38, width, borderRadius: 16 }} />
    </div>
  );
}

// ── ChatWindow ────────────────────────────────────────────────────────────────
export function ChatWindow({ chatId }: { chatId: string }) {
  const user     = useAuthStore((s) => s.user);
  const messages = useChatStore((s) => s.messages[chatId] ?? EMPTY_MESSAGES);
  const typingMap = useChatStore((s) => s.typing[chatId]  ?? EMPTY_TYPING);
  const chats    = useChatStore((s) => s.chats);
  const { sendMessage, sendTyping, isLoading } = useMessages(chatId);

  const activeChat = useMemo(() => chats.find((c) => c.id === chatId), [chats, chatId]);
  const peer       = activeChat ? getDmPeer(activeChat, user?.id || '') : null;

  // Only show typing from other users
  const typingNames = useMemo(
    () => typingMap.filter((t) => t.userId !== user?.id).map((t) => t.username),
    [typingMap, user?.id],
  );

  const [input,       setInput]      = useState('');
  const [replyingTo,  setReplyingTo] = useState<any | null>(null);
  const [editingId,   setEditingId]  = useState<string | null>(null);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; message: any } | null>(null);

  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const bottomRef        = useRef<HTMLDivElement>(null);
  const canvasRef        = useRef<HTMLDivElement>(null);
  const textareaRef      = useRef<HTMLTextAreaElement>(null);
  const initialScrollDone = useRef(false);

  // Join socket room for this chat
  useEffect(() => {
    const socket = getSocket();
    socket?.emit('join_chat', chatId);
    return () => { socket?.emit('leave_chat', chatId); };
  }, [chatId]);

  // Reset transient UI state on chat switch
  useEffect(() => {
    setReplyingTo(null);
    setContextMenu(null);
    setEditingId(null);
    initialScrollDone.current = false;
  }, [chatId]);

  // Scroll: jump to bottom on first load; then only scroll if already near bottom
  useEffect(() => {
    if (messages.length === 0) return;
    if (!initialScrollDone.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'instant' });
      initialScrollDone.current = true;
      return;
    }
    const canvas = canvasRef.current;
    if (!canvas) return;
    const distanceFromBottom = canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight;
    if (distanceFromBottom < 120) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  // Keep scroll at bottom when typing indicator appears
  useEffect(() => {
    if (typingNames.length === 0) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight < 120) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [typingNames]);

  // ── Handlers ────────────────────────────────────────────────────────────────

  const handleSend = useCallback(() => {
    if (!input.trim()) return;
    sendMessage(input, replyingTo?.id);
    setInput('');
    setReplyingTo(null);
    if (textareaRef.current) textareaRef.current.style.height = '40px';
    textareaRef.current?.focus();
    sendTyping(false);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 50);
  }, [input, replyingTo, sendMessage, sendTyping]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    const ta = e.target;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(ta.scrollHeight, 160)}px`;
    sendTyping(true);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => sendTyping(false), 2000);
  };

  const handleEditSave = useCallback((messageId: string, newContent: string) => {
    getSocket()?.emit('edit_message', { messageId, chatId, newContent });
    setEditingId(null);
  }, [chatId]);

  const handlePin = useCallback((messageId: string, currentlyPinned: boolean) => {
    getSocket()?.emit(currentlyPinned ? 'unpin_message' : 'pin_message', { messageId, chatId });
  }, [chatId]);

  const handleDelete = useCallback((messageId: string) => {
    getSocket()?.emit('delete_message', { messageId, chatId });
  }, [chatId]);

  const scrollToMessage = useCallback((id: string) => {
    const el = document.getElementById(`msg-${id}`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('msg-highlight');
    setTimeout(() => el.classList.remove('msg-highlight'), 1800);
  }, []);

  // Group messages by calendar day (memoised — O(n) per message list change)
  const grouped = useMemo(() => {
    const result: Array<{ date: string; messages: any[] }> = [];
    for (const msg of messages) {
      const last = result[result.length - 1];
      if (!last || !isSameDay(last.date, msg.createdAt)) {
        result.push({ date: msg.createdAt, messages: [msg] });
      } else {
        last.messages.push(msg);
      }
    }
    return result;
  }, [messages]);

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <>
      <div ref={canvasRef} className="chat-canvas" id="chat-canvas">

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

        {/* Empty state */}
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

        {/* Message list grouped by day */}
        {grouped.map(({ date, messages: dayMsgs }) => (
          <React.Fragment key={date}>
            <div className="date-separator">{formatDateSeparator(date)}</div>
            {dayMsgs.map((msg, i) => {
              const isOwn     = msg.senderId === user?.id;
              const prev      = dayMsgs[i - 1];
              // Collapse avatar/sender for consecutive messages from the same person
              const showMeta  = !prev || prev.senderId !== msg.senderId;
              return (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  isOwn={isOwn}
                  showMeta={showMeta}
                  isEditing={editingId === msg.id}
                  onContextMenu={(e, m) => {
                    e.preventDefault();
                    setContextMenu({ x: e.clientX, y: e.clientY, message: m });
                  }}
                  replyTo={msg.replyTo ?? null}
                  onScrollTo={scrollToMessage}
                  onEditSave={(newContent) => handleEditSave(msg.id, newContent)}
                  onEditCancel={() => setEditingId(null)}
                />
              );
            })}
          </React.Fragment>
        ))}

        {/* Typing indicator */}
        {typingNames.length > 0 && (
          <TypingIndicator names={typingNames} avatarUrl={peer?.avatarUrl} avatarUsername={peer?.username} />
        )}

        <div ref={bottomRef} />
      </div>

      {/* Reply bar shown above input when composing a reply */}
      {replyingTo && (
        <ReplyBar replyTo={replyingTo} onCancel={() => setReplyingTo(null)} />
      )}

      {/* Input bar */}
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

      {/* Context menu portal */}
      {contextMenu && (
        <MessageContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          isOwn={contextMenu.message.senderId === user?.id}
          isPinned={!!contextMenu.message.pinned}
          content={contextMenu.message.content}
          onClose={() => setContextMenu(null)}
          onReply={() => {
            setReplyingTo(contextMenu!.message);
            setContextMenu(null);
            textareaRef.current?.focus();
          }}
          onEdit={() => {
            setEditingId(contextMenu!.message.id);
            setContextMenu(null);
          }}
          onPin={() => handlePin(contextMenu!.message.id, !!contextMenu!.message.pinned)}
          onDelete={() => handleDelete(contextMenu!.message.id)}
        />
      )}
    </>
  );
}
