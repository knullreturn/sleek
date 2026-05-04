import React, { useRef, useState, useEffect } from 'react';
import { Avatar } from './Avatar';
import { ReplyChip } from './ReplyComponents';
import { formatMessageTime } from '../lib/utils';
import { Pin } from 'lucide-react';

// ── Types ─────────────────────────────────────────────────────────────────────
export interface MessageBubbleProps {
  message:       any;
  isOwn:         boolean;
  showMeta:      boolean;
  replyTo:       any | null;
  isEditing:     boolean;
  isSeen:        boolean;   // true when peer has read this message and hasn't replied yet
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
  peeking:     boolean;
  onPeekStart: () => void;
  onPeekEnd:   () => void;
}) {
  return (
    <span className="edited-tag-wrap">
      <button
        className={`edited-tag${peeking ? ' peeking' : ''}`}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); onPeekStart(); }}
        onPointerUp={onPeekEnd}
        onPointerLeave={onPeekEnd}
        onPointerCancel={onPeekEnd}
        onContextMenu={(e) => e.preventDefault()}  // prevent long-press menu on mobile
        title="Hold to see original"
      >
        {peeking ? 'original' : 'edited'}
      </button>
    </span>
  );
}

// ── MessageSkeleton ───────────────────────────────────────────────────────────
export function MessageSkeleton({ own, width }: { own?: boolean; width: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '4px 0', flexDirection: own ? 'row-reverse' : 'row' }}>
      {!own && <div className="skeleton" style={{ width: 32, height: 32, borderRadius: '50%', flexShrink: 0 }} />}
      <div className="skeleton" style={{ height: 38, width, borderRadius: 16 }} />
    </div>
  );
}

// ── MessageBubble ─────────────────────────────────────────────────────────────
export function MessageBubble({
  message, isOwn, showMeta, replyTo, isSeen,
  isEditing, onContextMenu, onScrollTo, onEditSave, onEditCancel,
}: MessageBubbleProps) {
  const [peekOriginal, setPeekOriginal] = useState(false);
  const canPeek        = message.edited && !!message.originalContent;
  const displayContent = peekOriginal && canPeek ? message.originalContent! : message.content;
  const isDeleted      = !!message.deletedAt;

  return (
    <div id={`msg-${message.id}`} className={`msg-row${isOwn ? ' own' : ''}`}>
      {/* Avatar column — only for messages from others */}
      {!isOwn && (
        <div style={{ width: 32, flexShrink: 0 }}>
          {showMeta && (
            <Avatar src={message.sender?.avatarUrl} username={message.sender?.username || '?'} size="sm" />
          )}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: isOwn ? 'flex-end' : 'flex-start', gap: 2, flex: 1, minWidth: 0 }}>
        {/* Sender name (shown only for first message in a group, not own) */}
        {showMeta && !isOwn && (
          <span className="msg-sender-name">{message.sender?.username}</span>
        )}

        {/* Reply chip */}
        {replyTo && <ReplyChip replyTo={replyTo} isOwn={isOwn} onScrollTo={onScrollTo} />}

        {/* ── Bubble variants ── */}
        {isDeleted ? (
          <div className={`msg-bubble${isOwn ? ' own' : ' other'} msg-bubble-deleted`}>
            <span style={{ float: 'right', fontSize: 10, opacity: 0.4, marginLeft: 8, marginTop: 4, position: 'relative', top: 3, whiteSpace: 'nowrap' }}>
              {formatMessageTime(message.createdAt)}
            </span>
            <span className="msg-deleted-text">🗑 This message was deleted</span>
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

            {/* Content — key remount re-triggers fade on peek swap */}
            <span
              key={peekOriginal ? 'orig' : 'curr'}
              className={`msg-content-text${peekOriginal ? ' content-peek' : ' content-current'}`}
              style={{ wordBreak: 'break-word', lineHeight: 1.55 }}
            >
              {displayContent}
            </span>

            {/* Meta: edited tag + timestamp */}
            <span className="msg-meta-row">
              {canPeek && (
                <EditedTag
                  peeking={peekOriginal}
                  onPeekStart={() => setPeekOriginal(true)}
                  onPeekEnd={() => setPeekOriginal(false)}
                />
              )}
              <span className={`msg-meta-time${isSeen ? ' seen' : ''}`}>
                {formatMessageTime(message.createdAt)}
              </span>
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
