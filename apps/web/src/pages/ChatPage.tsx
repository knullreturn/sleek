import React from 'react';
import { ChatList } from '../components/ChatList';
import { ChatWindow } from '../components/ChatWindow';
import { ChatHeader } from '../components/ChatHeader';
import { Sidebar } from '../components/Sidebar';
import { SearchModal } from '../components/SearchModal';
import { useChatStore } from '../store/chat.store';
import { useUIStore } from '../store/ui.store';
import { useAuthStore } from '../store/auth.store';
import { useSocket } from '../hooks/useSocket';
import { MessageSquare, LogOut } from 'lucide-react';

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId);
  const searchOpen = useUIStore((s) => s.searchOpen);
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  // Initialize socket connection
  useSocket();

  return (
    <div className="app-layout">
      {/* Global top header */}
      <header className="app-header" role="banner">
        <div className="app-logo">
          <div className="app-logo-mark" aria-hidden>S</div>
          <span className="app-logo-name">SLEEK</span>
        </div>

        <div style={{ flex: 1 }} />

        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              {user.handle}
            </span>
            <button
              id="logout-btn"
              className="icon-btn"
              onClick={clearAuth}
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={16} />
            </button>
          </div>
        )}
      </header>

      <div className="app-body">
        {/* Sidebar */}
        <Sidebar />

        {/* Chat list */}
        <ChatList />

        {/* Main chat area */}
        <main className="chat-main" role="main">
          {activeChatId ? (
            <>
              <ChatHeader chatId={activeChatId} />
              <ChatWindow chatId={activeChatId} />
            </>
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

      {/* Search modal */}
      {searchOpen && <SearchModal />}
    </div>
  );
}
