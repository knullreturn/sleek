import React from 'react';
import { ChatList } from '../components/ChatList';
import { ChatWindow } from '../components/ChatWindow';
import { ChatHeader } from '../components/ChatHeader';
import { Sidebar } from '../components/Sidebar';
import { SearchModal } from '../components/SearchModal';
import { useChatStore } from '../store/chat.store';
import { useUIStore } from '../store/ui.store';
import { useSocket } from '../hooks/useSocket';
import { MessageSquare } from 'lucide-react';

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId);
  const searchOpen = useUIStore((s) => s.searchOpen);

  useSocket();

  return (
    <div className="app-layout">
      {/* Top header — logo only */}
      <header className="app-header" role="banner">
        <div className="app-logo">
          <div className="app-logo-mark" aria-hidden>S</div>
          <span className="app-logo-name">SLEEK</span>
        </div>
      </header>

      <div className="app-body">
        <Sidebar />
        <ChatList />

        {/* Main chat area */}
        <main className="chat-main" role="main">
          {activeChatId ? (
            <>
              {/* Sub-header with peer name, status, actions */}
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

      {searchOpen && <SearchModal />}
    </div>
  );
}
