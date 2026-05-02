import React from 'react';
import { getInitials } from '../lib/utils';

interface AvatarProps {
  src?: string | null;
  username: string;
  size?: 'sm' | 'md' | 'lg';
  online?: boolean;
  className?: string;
}

export function Avatar({ src, username, size = 'md', online, className = '' }: AvatarProps) {
  return (
    <div className={`avatar-wrapper ${className}`}>
      {src ? (
        <img
          src={src}
          alt={username}
          className={`avatar avatar-${size}`}
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = 'none';
          }}
        />
      ) : (
        <div className={`avatar avatar-${size}`} aria-label={username}>
          {getInitials(username)}
        </div>
      )}
      {online !== undefined && online && <span className="online-dot" aria-label="Online" />}
    </div>
  );
}
