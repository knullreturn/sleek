import { Avatar } from './Avatar';
import { X } from 'lucide-react';

// ── ReplyChip ─────────────────────────────────────────────────────────────────
// Floating chip shown above a bubble indicating which message it replies to.
export function ReplyChip({ replyTo, isOwn, onScrollTo }: {
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
export function ReplyBar({ replyTo, onCancel }: {
  replyTo:  any;
  onCancel: () => void;
}) {
  return (
    <div className="reply-bar">
      <div className="reply-bar-accent" />
      <div className="reply-bar-body">
        <span className="reply-bar-label">
          Replying to <strong>{replyTo.sender?.username}</strong>
        </span>
        <span className="reply-bar-preview">{replyTo.content}</span>
      </div>
      <button
        className="icon-btn"
        onClick={onCancel}
        title="Cancel reply"
        aria-label="Cancel reply"
        style={{ flexShrink: 0 }}
      >
        <X size={14} />
      </button>
    </div>
  );
}
