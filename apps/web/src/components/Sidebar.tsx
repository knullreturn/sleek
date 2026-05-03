import React, { useState } from 'react';
import { MessageSquare, Sun, Moon } from 'lucide-react';
import { useUIStore } from '../store/ui.store';
import { useAuthStore } from '../store/auth.store';
import { Avatar } from './Avatar';
import { SettingsPage } from './SettingsPage';

export function Sidebar() {
  const { theme, toggleTheme, sidebarView, setSidebarView } = useUIStore();
  const user = useAuthStore((s) => s.user);
  const [settingsOpen, setSettingsOpen] = useState(false);

  return (
    <>
      <nav className="sidebar" aria-label="Main navigation">

        {/* Messages */}
        <button
          id="sidebar-messages"
          className={`sidebar-btn ${sidebarView === 'chats' ? 'active' : ''}`}
          onClick={() => setSidebarView('chats')}
          aria-label="Messages"
          title="Messages"
        >
          <MessageSquare size={20} />
          <span className="tooltip">Messages</span>
        </button>

        {/* Spacer */}
        <div style={{ flex: 1 }} />

        {/* Theme toggle */}
        <button
          id="theme-toggle"
          className="sidebar-btn"
          onClick={toggleTheme}
          aria-label="Toggle theme"
          title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          <span className="tooltip">{theme === 'dark' ? 'Light mode' : 'Dark mode'}</span>
        </button>

        {/* Avatar — opens Settings */}
        {user && (
          <button
            id="profile-btn"
            className="sidebar-btn"
            onClick={() => setSettingsOpen(true)}
            aria-label="Settings"
            title="Settings"
            style={{ padding: 0, width: 44, height: 44 }}
          >
            <Avatar src={user.avatarUrl} username={user.username ?? ''} size="sm" />
            <span className="tooltip">Settings</span>
          </button>
        )}
      </nav>

      {settingsOpen && <SettingsPage onClose={() => setSettingsOpen(false)} />}
    </>
  );
}
