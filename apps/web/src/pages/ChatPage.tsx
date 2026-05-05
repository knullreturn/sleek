import React, { useState } from 'react';
import { ChatList } from '../components/ChatList';
import { ChatWindow } from '../components/ChatWindow';
import { Sidebar } from '../components/Sidebar';
import { SearchModal } from '../components/SearchModal';
import { PinnedPanel } from '../components/PinnedPanel';
import { Avatar } from '../components/Avatar';
import { useChatStore } from '../store/chat.store';
import { useAuthStore } from '../store/auth.store';
import { useUIStore } from '../store/ui.store';
import { useSocket } from '../hooks/useSocket';
import { getDmPeer } from '../lib/utils';
import { MessageSquare, Search, MoreHorizontal, PanelLeftClose, PanelLeftOpen, Pin } from 'lucide-react';

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId);
  const chats = useChatStore((s) => s.chats);
  const onlineUsers  = useChatStore((s) => s.onlineUsers);
  const sleepingUsers = useChatStore((s) => s.sleepingUsers);
  const searchOpen = useUIStore((s) => s.searchOpen);
  const chatListOpen = useUIStore((s) => s.chatListOpen);
  const setSearchOpen = useUIStore((s) => s.setSearchOpen);
  const toggleChatList = useUIStore((s) => s.toggleChatList);
  const user = useAuthStore((s) => s.user);

  const activeChat  = chats.find((c) => c.id === activeChatId);
  const peer        = activeChat ? getDmPeer(activeChat, user?.id || '') : null;
  const isOnline    = peer ? onlineUsers.has(peer.id) : false;
  const isSleeping  = peer ? sleepingUsers.has(peer.id) : false;

  // Count pinned messages — selector returns a number (primitive) to avoid
  // infinite re-renders caused by new array references on every selector call
  const pinnedCount = useChatStore((s) =>
    activeChatId
      ? (s.messages[activeChatId] ?? []).filter((m: any) => m.pinned).length
      : 0
  );
  const [pinPanelOpen, setPinPanelOpen] = useState(false);

  useSocket();

  return (
    <div className="app-layout">
      <header className="app-header" role="banner">
        {/* Left cell — shrinks/grows with the chat list panel */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, paddingLeft: 16,
          minWidth: chatListOpen
            ? 'calc(var(--sidebar-width) + var(--chatlist-width))'
            : 'var(--sidebar-width)',
          transition: 'min-width 220ms cubic-bezier(0.4,0,0.2,1)',
          overflow: 'hidden',
        }}>
          <div className="app-logo">
            <img src="/sleek_logo.png" alt="SLEEK" style={{ width: 28, height: 28, objectFit: 'contain', flexShrink: 0 }} />
            {/* Brand name fades out when chatlist is collapsed */}
            <span
              className="app-logo-name"
              style={{
                opacity: chatListOpen ? 1 : 0,
                maxWidth: chatListOpen ? 80 : 0,
                overflow: 'hidden',
                whiteSpace: 'nowrap',
                transition: 'opacity 180ms ease, max-width 220ms cubic-bezier(0.4,0,0.2,1)',
              }}
            >
              SLEEK
            </span>
          </div>
          <button
            id="toggle-chatlist-btn"
            className="icon-btn"
            onClick={toggleChatList}
            title={chatListOpen ? 'Hide sidebar' : 'Show sidebar'}
            aria-label={chatListOpen ? 'Hide sidebar' : 'Show sidebar'}
            style={{ marginLeft: 4, flexShrink: 0 }}
          >
            {chatListOpen ? <PanelLeftClose size={17} /> : <PanelLeftOpen size={17} />}
          </button>
        </div>

        {/* Right cell — peer info */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 }}>
          {peer ? (
            <>
              <Avatar src={peer.avatarUrl} username={peer.username} size="sm" online={isOnline && !isSleeping} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-primary)', lineHeight: 1.2 }}>
                  {peer.username}
                </div>
                <div style={{
                  fontSize: 11,
                  color: isSleeping ? 'var(--accent, #7c6af7)' : isOnline ? 'var(--online)' : 'var(--text-muted)',
                  lineHeight: 1,
                  marginTop: 4,
                }}>
                  {isSleeping ? '💤 Do Not Disturb' : isOnline ? '● Online' : 'Offline'}
                </div>
              </div>
              <button
                id="header-pin-btn"
                className="icon-btn"
                onClick={() => setPinPanelOpen((o) => !o)}
                title="Pinned messages"
                aria-label="Pinned messages"
                style={{ position: 'relative' }}
              >
                <Pin size={16} />
                {pinnedCount > 0 && (
                  <span className="pin-count-badge">{pinnedCount}</span>
                )}
              </button>
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

        {/* Chat list — collapsible wrapper */}
        <div
          className="chat-list-wrapper"
          style={{
            width:     chatListOpen ? 'var(--chatlist-width)' : 0,
            minWidth:  chatListOpen ? 'var(--chatlist-width)' : 0,
            overflow:  'hidden',
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

      {/* Pinned messages panel */}
      {pinPanelOpen && activeChatId && (
        <PinnedPanel
          chatId={activeChatId}
          onClose={() => setPinPanelOpen(false)}
          onJump={(msgId) => {
            const el = document.getElementById(`msg-${msgId}`);
            if (!el) return;
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            el.classList.add('msg-highlight');
            setTimeout(() => el.classList.remove('msg-highlight'), 1800);
          }}
        />
      )}
    </div>
  );
}
