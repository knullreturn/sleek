import React from 'react';
import { ChatList } from '../components/ChatList';
import { ChatWindow } from '../components/ChatWindow';
import { Sidebar } from '../components/Sidebar';
import { SearchModal } from '../components/SearchModal';
import { Avatar } from '../components/Avatar';
import { useChatStore } from '../store/chat.store';
import { useAuthStore } from '../store/auth.store';
import { useUIStore } from '../store/ui.store';
import { useSocket } from '../hooks/useSocket';
import { getDmPeer } from '../lib/utils';
import { MessageSquare, Search, MoreHorizontal } from 'lucide-react';

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId);
  const chats = useChatStore((s) => s.chats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const searchOpen = useUIStore((s) => s.searchOpen);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);
  const user = useAuthStore((s) => s.user);

  // Active chat peer info
  const activeChat = chats.find((c) => c.id === activeChatId);
  const peer = activeChat ? getDmPeer(activeChat, user?.id || '') : null;
  const isOnline = peer ? onlineUsers.has(peer.id) : false;

  useSocket();

  return (
    <div className="app-layout">
      {/* Global top header */}
      <header className="app-header" role="banner">
        {/* Logo — always visible */}
        <div className="app-logo">
          <div className="app-logo-mark" aria-hidden>S</div>
          <span className="app-logo-name">SLEEK</span>
        </div>

        {/* Active chat info — shown in header when a chat is open */}
        {peer ? (
          <>
            <div style={{ width: 1, height: 24, background: 'var(--border-subtle)', margin: '0 12px' }} />
            <Avatar src={peer.avatarUrl} username={peer.username} size="sm" online={isOnline} />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-primary)', lineHeight: 1.2 }}>
                {peer.username}
              </div>
              <div style={{ fontSize: 11, color: isOnline ? 'var(--online)' : 'var(--text-muted)' }}>
                {isOnline ? 'Online' : 'Offline'}
              </div>
            </div>
            <button
              id="header-search-btn"
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
          </>
        ) : (
          <div style={{ flex: 1 }} />
        )}
      </header>

      <div className="app-body">
        <Sidebar />
        <ChatList />

        {/* Main chat area — no separate sub-header */}
        <main className="chat-main" role="main">
          {activeChatId ? (
            <ChatWindow chatId={activeChatId} />
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">
                <MessageSquare size={28} />
              </div>
              <h3>No conversation selected</h3>
              <p>Choose a conversation from the sidebar or start a new one</p>
            </div>
          )}
        </main>
      </div>

      {searchOpen && <SearchModal />}
    </div>
  );
}
