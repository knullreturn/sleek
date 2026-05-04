import React from 'react';
import { Avatar } from './Avatar';

interface TypingIndicatorProps {
  names:         string[];
  avatarUrl?:    string;
  avatarUsername?: string;
  fadingOut?:    boolean;   // keeps element mounted but plays exit animation
}

export function TypingIndicator({ names, avatarUrl, avatarUsername, fadingOut }: TypingIndicatorProps) {
  return (
    <div className={`typing-message-row${fadingOut ? ' fading-out' : ''}`}>
      <Avatar src={avatarUrl} username={avatarUsername || '?'} size="sm" />
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
