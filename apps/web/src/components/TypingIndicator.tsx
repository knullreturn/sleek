import React from 'react';

interface TypingIndicatorProps {
  names: string[];
  avatarUrl?: string;
}

export function TypingIndicator({ names, avatarUrl }: TypingIndicatorProps) {
  if (names.length === 0) return null;

  return (
    <div className="typing-message-row">
      {/* Avatar */}
      <div
        className="avatar avatar-sm"
        style={{
          background: 'linear-gradient(135deg, #8b5cf6, #4c1d95)',
          boxShadow: '0 0 10px rgba(139,92,246,0.4)',
          flexShrink: 0,
        }}
      >
        {avatarUrl ? (
          <img src={avatarUrl} alt="" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
        ) : null}
      </div>

      {/* Bubble with mini keyboard */}
      <div className="typing-bubble">
        <div className="mini-keyboard">
          <div className="m-key delay-1" />
          <div className="m-key delay-2" />
          <div className="m-key delay-3" />
          <div className="m-key delay-4" />
          <div className="m-key delay-5" />
        </div>
      </div>
    </div>
  );
}
