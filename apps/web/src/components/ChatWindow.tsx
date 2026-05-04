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
import { useMessageScroll } from '../hooks/useMessageScroll';

const EMPTY_MESSAGES: any[] = [];
const EMPTY_TYPING:   any[] = [];

export function ChatWindow({ chatId }: { chatId: string }) {
  const user       = useAuthStore((s) => s.user);
  const messages   = useChatStore((s) => s.messages[chatId] ?? EMPTY_MESSAGES);
  const typingMap  = useChatStore((s) => s.typing[chatId]   ?? EMPTY_TYPING);
  const chats      = useChatStore((s) => s.chats);
  const seenUpToId = useChatStore((s) => s.seenUpTo[chatId] ?? null);
  const { sendMessage, sendTyping, isLoading } = useMessages(chatId);

  const activeChat  = useMemo(() => chats.find((c) => c.id === chatId), [chats, chatId]);
  const peer        = activeChat ? getDmPeer(activeChat, user?.id || '') : null;
  const typingNames = useMemo(
    () => typingMap.filter((t) => t.userId !== user?.id).map((t) => t.username),
    [typingMap, user?.id],
  );

  // ── Local UI state ─────────────────────────────────────────────────────────
  const [input,       setInput]       = useState('');
  const [replyingTo,  setReplyingTo]  = useState<any | null>(null);
  const [editingId,   setEditingId]   = useState<string | null>(null);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; message: any } | null>(null);

  const typingTimeoutRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const textareaRef       = useRef<HTMLTextAreaElement>(null);
  const typingFadeTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Delayed unmount for typing indicator — keeps it alive for 350ms while fading
  // out, so the height doesn't collapse before the new message fills the space.
  const [visibleTyping, setVisibleTyping] = useState(false);
  const [typingFadingOut, setTypingFadingOut] = useState(false);

  useEffect(() => {
    if (typingNames.length > 0) {
      if (typingFadeTimer.current) clearTimeout(typingFadeTimer.current);
      setVisibleTyping(true);
      setTypingFadingOut(false);
    } else if (visibleTyping) {
      setTypingFadingOut(true);
      typingFadeTimer.current = setTimeout(() => {
        setVisibleTyping(false);
        setTypingFadingOut(false);
      }, 350);
    }
    return () => { if (typingFadeTimer.current) clearTimeout(typingFadeTimer.current); };
  }, [typingNames, visibleTyping]);

  // Reset compose state on chat switch
  useEffect(() => {
    setReplyingTo(null);
    setContextMenu(null);
    setEditingId(null);
    setVisibleTyping(false);
    setTypingFadingOut(false);
  }, [chatId]);

  // ── Scroll (extracted hook) ────────────────────────────────────────────────
  const { canvasRef, bottomRef, isScrolledUp, newMsgCount, scrollToBottom } = useMessageScroll({
    chatId,
    messages,
    myId: user?.id,
    typingNames,
  });

  // ── Socket room ────────────────────────────────────────────────────────────
  useEffect(() => {
    const socket = getSocket();
    socket?.emit('join_chat', chatId);
    return () => { socket?.emit('leave_chat', chatId); };
  }, [chatId]);

  // ── Read receipts ──────────────────────────────────────────────────────────
  useEffect(() => {
    const emitSeen = () => {
      if (document.visibilityState !== 'visible') return;
      const lastPeerMsg = [...messages].reverse().find((m) => m.senderId !== user?.id);
      if (lastPeerMsg) getSocket()?.emit('mark_seen', { chatId, messageId: lastPeerMsg.id });
    };
    emitSeen();
    document.addEventListener('visibilitychange', emitSeen);
    return () => document.removeEventListener('visibilitychange', emitSeen);
  }, [chatId, messages, user?.id]);

  // ── Derived ────────────────────────────────────────────────────────────────
  const peerHasReplied = useMemo(() => {
    if (messages.length === 0) return false;
    return messages[messages.length - 1].senderId !== user?.id;
  }, [messages, user?.id]);

  const grouped = useMemo(() => {
    const result: Array<{ date: string; messages: any[] }> = [];
    for (const msg of messages) {
      const last = result[result.length - 1];
      if (!last || !isSameDay(last.date, msg.createdAt)) result.push({ date: msg.createdAt, messages: [msg] });
      else last.messages.push(msg);
    }
    return result;
  }, [messages]);

  // ── Handlers ───────────────────────────────────────────────────────────────
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
  }, [input, replyingTo, sendMessage, sendTyping, bottomRef]);

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

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      <div ref={canvasRef} className="chat-canvas" id="chat-canvas">
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
              const isOwn    = msg.senderId === user?.id;
              const prev     = dayMsgs[i - 1];
              const showMeta = !prev || prev.senderId !== msg.senderId;
              const isSeen   = isOwn && !peerHasReplied && msg.id === seenUpToId;
              return (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  isOwn={isOwn}
                  showMeta={showMeta}
                  isSeen={isSeen}
                  isEditing={editingId === msg.id}
                  onContextMenu={(e, m) => { e.preventDefault(); setContextMenu({ x: e.clientX, y: e.clientY, message: m }); }}
                  replyTo={msg.replyTo ?? null}
                  onScrollTo={scrollToMessage}
                  onEditSave={(newContent) => handleEditSave(msg.id, newContent)}
                  onEditCancel={() => setEditingId(null)}
                />
              );
            })}
          </React.Fragment>
        ))}

        {visibleTyping && (
          <TypingIndicator
            names={typingNames}
            avatarUrl={peer?.avatarUrl}
            avatarUsername={peer?.username}
            fadingOut={typingFadingOut}
          />
        )}

        <div ref={bottomRef} />
      </div>

      {isScrolledUp && <ScrollToBottomBtn count={newMsgCount} onClick={scrollToBottom} />}

      {replyingTo && <ReplyBar replyTo={replyingTo} onCancel={() => setReplyingTo(null)} />}

      <ChatInput
        input={input}
        onInputChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onSend={handleSend}
        textareaRef={textareaRef}
      />

      {contextMenu && (
        <MessageContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          isOwn={contextMenu.message.senderId === user?.id}
          isPinned={!!contextMenu.message.pinned}
          content={contextMenu.message.content}
          onClose={() => setContextMenu(null)}
          onReply={() => { setReplyingTo(contextMenu!.message); setContextMenu(null); textareaRef.current?.focus(); }}
          onEdit={() => { setEditingId(contextMenu!.message.id); setContextMenu(null); }}
          onPin={() => handlePin(contextMenu!.message.id, !!contextMenu!.message.pinned)}
          onDelete={() => handleDelete(contextMenu!.message.id)}
        />
      )}
    </>
  );
}
