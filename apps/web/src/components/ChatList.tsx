import React, { useState, useMemo } from 'react';
import { Avatar } from './Avatar';
import { formatChatTime, getDmPeer } from '../lib/utils';
import { useChatStore } from '../store/chat.store';
import { useAuthStore } from '../store/auth.store';
import { useChats } from '../hooks/useData';
import { Plus, Search } from 'lucide-react';
import { useUIStore } from '../store/ui.store';

function ChatItemSkeleton() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px' }}>
      <div className="skeleton" style={{ width: 40, height: 40, borderRadius: '50%', flexShrink: 0 }} />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div className="skeleton" style={{ height: 12, width: '55%', borderRadius: 6 }} />
        <div className="skeleton" style={{ height: 10, width: '80%', borderRadius: 6 }} />
      </div>
      <div className="skeleton" style={{ height: 10, width: 28, borderRadius: 6 }} />
    </div>
  );
}

export function ChatList() {
  const user = useAuthStore((s) => s.user);
  const chats = useChatStore((s) => s.chats);
  const activeChatId = useChatStore((s) => s.activeChatId);
  const setActiveChatId = useChatStore((s) => s.setActiveChatId);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);
  const [filter, setFilter] = useState('');

  const { isLoading } = useChats();

  // Filter existing chats by peer name (client-side — no API call)
  const filteredChats = useMemo(() => {
    if (!filter.trim()) return chats;
    const q = filter.toLowerCase();
    return chats.filter((chat) => {
      const peer = getDmPeer(chat, user?.id || '');
      return peer?.username?.toLowerCase().includes(q);
    });
  }, [chats, filter, user?.id]);

  return (
    <div className="chat-list-panel" style={{ display: 'flex', flexDirection: 'column' }}>
      {/* Panel header */}
      <div style={{ padding: '14px 14px 10px', borderBottom: '1px solid var(--border-subtle)', flexShrink: 0 }}>
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

        {/* Inline filter — searches existing friends only */}
        <div style={{ position: 'relative' }}>
          <Search size={14} style={{
            position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)',
            color: 'var(--text-placeholder)', pointerEvents: 'none',
          }} />
          <input
            id="chat-filter-input"
            type="text"
            placeholder="Filter conversations…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            style={{
              width: '100%',
              padding: '8px 12px 8px 30px',
              background: 'var(--bg-input)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-md)',
              color: 'var(--text-primary)',
              fontSize: 13,
              fontFamily: 'inherit',
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
        </div>
      </div>

      {/* Chat items */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {isLoading && (
          <>
            <ChatItemSkeleton />
            <ChatItemSkeleton />
            <ChatItemSkeleton />
            <ChatItemSkeleton />
            <ChatItemSkeleton />
          </>
        )}

        {!isLoading && filteredChats.length === 0 && (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            {filter ? `No chats matching "${filter}"` : 'No conversations yet.'}
            {!filter && (
              <>
                <br />
                <button
                  onClick={() => setSearchOpen(true)}
                  style={{ marginTop: 12, color: 'var(--accent)', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, fontFamily: 'inherit' }}
                >
                  Start one →
                </button>
              </>
            )}
          </div>
        )}

        {filteredChats.map((chat) => {
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
              <Avatar src={peer?.avatarUrl} username={peer?.username || '?'} size="md" online={isOnline} />
              <div className="chat-item-meta">
                <div className="chat-item-name">{peer?.username || 'Unknown'}</div>
                <div className="chat-item-preview">
                  {lastMsg
                    ? lastMsg.senderId === user?.id ? `You: ${lastMsg.content}` : lastMsg.content
                    : 'No messages yet'}
                </div>
              </div>
              {lastMsg && <span className="chat-item-time">{formatChatTime(lastMsg.createdAt)}</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}
