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
import { MessageSquare, Search, MoreHorizontal, PanelLeftClose, PanelLeftOpen } from 'lucide-react';

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId);
  const chats = useChatStore((s) => s.chats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const searchOpen = useUIStore((s) => s.searchOpen);
  const chatListOpen = useUIStore((s) => s.chatListOpen);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);
  const toggleChatList = useUIStore((s) => s.toggleChatList);
  const user = useAuthStore((s) => s.user);

  const activeChat = chats.find((c) => c.id === activeChatId);
  const peer = activeChat ? getDmPeer(activeChat, user?.id || '') : null;
  const isOnline = peer ? onlineUsers.has(peer.id) : false;

  useSocket();

  return (
    <div className="app-layout">
      <header className="app-header" role="banner">
        {/* Left cell — logo + toggle button */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div className="app-logo">
            <img
              src="/sleek_logo.png"
              alt="SLEEK"
              style={{ width: 28, height: 28, objectFit: 'contain' }}
            />
            <span className="app-logo-name">SLEEK</span>
          </div>
          <button
            id="toggle-chatlist-btn"
            className="icon-btn"
            onClick={toggleChatList}
            title={chatListOpen ? 'Hide sidebar' : 'Show sidebar'}
            aria-label={chatListOpen ? 'Hide sidebar' : 'Show sidebar'}
            style={{ marginLeft: 4 }}
          >
            {chatListOpen ? <PanelLeftClose size={17} /> : <PanelLeftOpen size={17} />}
          </button>
        </div>

        {/* Right cell — peer info */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 }}>
          {peer ? (
            <>
              <Avatar src={peer.avatarUrl} username={peer.username} size="sm" online={isOnline} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-primary)', lineHeight: 1.2 }}>
                  {peer.username}
                </div>
                <div style={{ fontSize: 11, color: isOnline ? 'var(--online)' : 'var(--text-muted)', lineHeight: 1 }}>
                  {isOnline ? 'Online' : 'Offline'}
                </div>
              </div>
              <button
                id="header-search-btn"
                className="icon-btn"
                onClick={() => setSearchOpen(true)}
                title="New chat"
                aria-label="New chat"
              >
                <Search size={16} />
              </button>
              <button className="icon-btn" title="More" aria-label="More options">
                <MoreHorizontal size={16} />
              </button>
            </>
          ) : null}
        </div>
      </header>

      <div className="app-body">
        <Sidebar />

        {/* Chat list — collapsible */}
        <div
          className="chat-list-panel"
          style={{
            width: chatListOpen ? 'var(--chatlist-width)' : 0,
            minWidth: chatListOpen ? 'var(--chatlist-width)' : 0,
            overflow: 'hidden',
            transition: 'width 220ms cubic-bezier(0.4,0,0.2,1), min-width 220ms cubic-bezier(0.4,0,0.2,1)',
          }}
        >
          {chatListOpen && <ChatList />}
        </div>

        <main className="chat-main" role="main">
          {activeChatId ? (
            <ChatWindow chatId={activeChatId} />
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">
                <MessageSquare size={28} />
              </div>
              <h3>No conversation selected</h3>
              <p>Choose a conversation or start a new one</p>
            </div>
          )}
        </main>
      </div>

      {searchOpen && <SearchModal />}
    </div>
  );
}
