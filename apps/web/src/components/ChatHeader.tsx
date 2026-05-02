import React from 'react';
import { Avatar } from './Avatar';
import { getDmPeer } from '../lib/utils';
import { useChatStore } from '../store/chat.store';
import { useAuthStore } from '../store/auth.store';
import { Search, Phone, MoreHorizontal } from 'lucide-react';
import { useUIStore } from '../store/ui.store';

export function ChatHeader({ chatId }: { chatId: string }) {
  const user = useAuthStore((s) => s.user);
  const chats = useChatStore((s) => s.chats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);

  const chat = chats.find((c) => c.id === chatId);
  const peer = chat ? getDmPeer(chat, user?.id || '') : null;
  const isOnline = peer && onlineUsers.has(peer.id);

  if (!peer) return <div className="chat-main-header" />;

  return (
    <div className="chat-main-header">
      <Avatar src={peer.avatarUrl} username={peer.username} size="sm" online={isOnline} />
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-primary)' }}>
          {peer.username}
        </div>
        <div style={{ fontSize: 11, color: isOnline ? 'var(--online)' : 'var(--text-muted)' }}>
          {isOnline ? 'Online' : 'Offline'}
        </div>
      </div>

      <button
        className="icon-btn"
        onClick={() => setSearchOpen(true)}
        title="Search"
        aria-label="Search"
      >
        <Search size={16} />
      </button>
      <button className="icon-btn" title="More options" aria-label="More options">
        <MoreHorizontal size={16} />
      </button>
    </div>
  );
}
