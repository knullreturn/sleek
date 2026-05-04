import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { TypingIndicator } from './TypingIndicator';
import { MessageContextMenu } from './MessageContextMenu';
import { MessageBubble, MessageSkeleton } from './MessageBubble';
import { ReplyBar } from './ReplyComponents';
import { ChatInput } from './ChatInput';
import { ScrollToBottomBtn } from './ScrollToBottomBtn';
import { formatDateSeparator, isSameDay, getDmPeer } from '../lib/utils';
import { useAuthStore } from '../store/auth.store';
import { useChatStore } from '../store/chat.store';
import { useMessages } from '../hooks/useData';
import { getSocket } from '../hooks/useSocket';

// Stable empty references — prevent new array identity on every render
const EMPTY_MESSAGES: any[] = [];
const EMPTY_TYPING:   any[] = [];

export function ChatWindow({ chatId }: { chatId: string }) {
  const user      = useAuthStore((s) => s.user);
  const messages  = useChatStore((s) => s.messages[chatId] ?? EMPTY_MESSAGES);
  const typingMap = useChatStore((s) => s.typing[chatId]  ?? EMPTY_TYPING);
  const chats     = useChatStore((s) => s.chats);
  const seenUpToId = useChatStore((s) => s.seenUpTo[chatId] ?? null);
  const { sendMessage, sendTyping, isLoading } = useMessages(chatId);

  const activeChat  = useMemo(() => chats.find((c) => c.id === chatId), [chats, chatId]);
  const peer        = activeChat ? getDmPeer(activeChat, user?.id || '') : null;
  const typingNames = useMemo(
    () => typingMap.filter((t) => t.userId !== user?.id).map((t) => t.username),
    [typingMap, user?.id],
  );

  const [input,        setInput]       = useState('');
  const [replyingTo,   setReplyingTo]  = useState<any | null>(null);
  const [editingId,    setEditingId]   = useState<string | null>(null);
  const [contextMenu,  setContextMenu] = useState<{ x: number; y: number; message: any } | null>(null);
  const [isScrolledUp, setIsScrolledUp] = useState(false);
  const [newMsgCount,  setNewMsgCount]  = useState(0);

  const typingTimeoutRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const bottomRef         = useRef<HTMLDivElement>(null);
  const canvasRef         = useRef<HTMLDivElement>(null);
  const textareaRef       = useRef<HTMLTextAreaElement>(null);
  const initialScrollDone = useRef(false);
  const isScrolledUpRef   = useRef(false);  // ref for use inside effects without stale closure

  // ── Socket room ────────────────────────────────────────────────────────────
  useEffect(() => {
    const socket = getSocket();
    socket?.emit('join_chat', chatId);
    return () => { socket?.emit('leave_chat', chatId); };
  }, [chatId]);

  // ── Reset state on chat switch ─────────────────────────────────────────────
  useEffect(() => {
    setReplyingTo(null);
    setContextMenu(null);
    setEditingId(null);
    setIsScrolledUp(false);
    setNewMsgCount(0);
    isScrolledUpRef.current = false;
    initialScrollDone.current = false;
  }, [chatId]);

  // ── Scroll event: track if user is scrolled up ─────────────────────────────
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const onScroll = () => {
      const distFromBottom = canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight;
      const scrolledUp = distFromBottom > 120;
      setIsScrolledUp(scrolledUp);
      isScrolledUpRef.current = scrolledUp;
      // Clear count when they scroll back down
      if (!scrolledUp) setNewMsgCount(0);
    };
    canvas.addEventListener('scroll', onScroll, { passive: true });
    return () => canvas.removeEventListener('scroll', onScroll);
  }, [chatId]);

  // ── Scroll: jump on first load; auto-scroll or count new messages ──────────
  useEffect(() => {
    if (messages.length === 0) return;
    if (!initialScrollDone.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'instant' });
      initialScrollDone.current = true;
      return;
    }
    const lastMsg = messages[messages.length - 1];
    const isOwnMessage = lastMsg?.senderId === user?.id;

    if (isScrolledUpRef.current && !isOwnMessage) {
      // User is reading old messages — show FAB count instead of auto-scrolling
      setNewMsgCount((c) => c + 1);
    } else {
      // Near bottom or own message sent — auto-scroll
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, user?.id]);

  // ── Scroll: keep bottom in view when typing indicator appears ─────────────
  useEffect(() => {
    if (typingNames.length === 0) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight < 120) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [typingNames]);

  // ── Read receipts: emit mark_seen when chat is visible ─────────────────────
  useEffect(() => {
    const emitSeen = () => {
      if (document.visibilityState !== 'visible') return;
      // Find the last message from the peer (not from us)
      const lastPeerMsg = [...messages].reverse().find((m) => m.senderId !== user?.id);
      if (lastPeerMsg) {
        getSocket()?.emit('mark_seen', { chatId, messageId: lastPeerMsg.id });
      }
    };
    emitSeen();
    document.addEventListener('visibilitychange', emitSeen);
    return () => document.removeEventListener('visibilitychange', emitSeen);
  }, [chatId, messages, user?.id]);

  // ── Seen index: which of our messages has the peer read up to? ─────────────
  // Map message IDs → flat index for O(1) bubble lookup
  const msgIndexMap = useMemo(() => {
    const map = new Map<string, number>();
    messages.forEach((m, i) => map.set(m.id, i));
    return map;
  }, [messages]);

  const seenUpToIdx = useMemo(() => {
    if (!seenUpToId) return -1;
    return msgIndexMap.get(seenUpToId) ?? -1;
  }, [seenUpToId, msgIndexMap]);

  // If the most recent message in the chat is from the peer, they have replied —
  // no timestamps should be green regardless of seenUpToId state.
  const peerHasReplied = useMemo(() => {
    if (messages.length === 0) return false;
    const last = messages[messages.length - 1];
    return last.senderId !== user?.id;
  }, [messages, user?.id]);

  // ── Handlers ───────────────────────────────────────────────────────────────

  const scrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    setNewMsgCount(0);
  }, []);

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
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
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
    const row = document.getElementById(`msg-${id}`);
    if (!row) return;
    row.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const bubble = row.querySelector<HTMLElement>('.msg-bubble');
    if (!bubble) return;
    bubble.classList.add('msg-highlight');
    setTimeout(() => bubble.classList.remove('msg-highlight'), 1600);
  }, []);

  // ── Group messages by calendar day ─────────────────────────────────────────
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

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      {/* Canvas — the scrollable message area */}
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
              const isOwn    = msg.senderId === user?.id;
              const prev     = dayMsgs[i - 1];
              const showMeta = !prev || prev.senderId !== msg.senderId;
              // Flat index of this message in the full messages array
              const flatIdx  = msgIndexMap.get(msg.id) ?? -1;
              // Green: own msg + peer saw it + peer hasn't replied since
              const isSeen   = isOwn && !peerHasReplied && seenUpToIdx >= 0 && flatIdx <= seenUpToIdx;
              return (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  isOwn={isOwn}
                  showMeta={showMeta}
                  isSeen={isSeen}
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

      {/* Scroll-to-bottom FAB — shown when scrolled up */}
      {isScrolledUp && (
        <ScrollToBottomBtn count={newMsgCount} onClick={scrollToBottom} />
      )}

      {/* Reply bar */}
      {replyingTo && (
        <ReplyBar replyTo={replyingTo} onCancel={() => setReplyingTo(null)} />
      )}

      {/* Input */}
      <ChatInput
        input={input}
        onInputChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onSend={handleSend}
        textareaRef={textareaRef}
      />

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
