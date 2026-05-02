import React from 'react';
import { Avatar } from './Avatar';
import { formatChatTime, getDmPeer } from '../lib/utils';
import { useChatStore } from '../store/chat.store';
import { useAuthStore } from '../store/auth.store';
import { useChats } from '../hooks/useData';
import { Plus } from 'lucide-react';
import { useUIStore } from '../store/ui.store';

export function ChatList() {
  const user = useAuthStore((s) => s.user);
  // Proper selectors — only re-render when specific slice changes
  const chats = useChatStore((s) => s.chats);
  const activeChatId = useChatStore((s) => s.activeChatId);
  const setActiveChatId = useChatStore((s) => s.setActiveChatId);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);

  useChats(); // triggers fetch + sync

  return (
    <div className="chat-list-panel">
      {/* Panel header */}
      <div style={{ padding: '14px 14px 10px', borderBottom: '1px solid var(--border-subtle)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <span style={{ fontWeight: 600, fontSize: 15, color: 'var(--text-primary)' }}>Messages</span>
          <button
            id="new-chat-btn"
            className="icon-btn"
            onClick={() => setSearchOpen(true)}
            title="New chat"
            aria-label="New chat"
          >
            <Plus size={18} />
          </button>
        </div>

        {/* Search bar */}
        <button
          id="open-search-btn"
          onClick={() => setSearchOpen(true)}
          style={{
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 12px',
            background: 'var(--bg-input)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-md)',
            color: 'var(--text-placeholder)',
            fontSize: 13,
            cursor: 'text',
            fontFamily: 'inherit',
          }}
        >
          Find or start a conversation
        </button>
      </div>

      {/* Chat items */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {chats.length === 0 && (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            No conversations yet.
            <br />
            <button
              onClick={() => setSearchOpen(true)}
              style={{
                marginTop: 12,
                color: 'var(--accent)',
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                fontSize: 13,
                fontFamily: 'inherit',
              }}
            >
              Start one →
            </button>
          </div>
        )}

        {chats.map((chat) => {
          const peer = getDmPeer(chat, user?.id || '');
          const isActive = chat.id === activeChatId;
          const isOnline = peer ? onlineUsers.has(peer.id) : false;
          const lastMsg = chat.lastMessage;

          return (
            <div
              key={chat.id}
              id={`chat-item-${chat.id}`}
              className={`chat-item ${isActive ? 'active' : ''}`}
              onClick={() => setActiveChatId(chat.id)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && setActiveChatId(chat.id)}
              aria-selected={isActive}
            >
              <Avatar
                src={peer?.avatarUrl}
                username={peer?.username || '?'}
                size="md"
                online={isOnline}
              />
              <div className="chat-item-meta">
                <div className="chat-item-name">{peer?.username || 'Unknown'}</div>
                <div className="chat-item-preview">
                  {lastMsg
                    ? lastMsg.senderId === user?.id
                      ? `You: ${lastMsg.content}`
                      : lastMsg.content
                    : 'No messages yet'}
                </div>
              </div>
              {lastMsg && (
                <span className="chat-item-time">{formatChatTime(lastMsg.createdAt)}</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
