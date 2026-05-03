import React, { useState } from 'react';
import { MessageSquare, Settings, Sun, Moon } from 'lucide-react';
import { useUIStore } from '../store/ui.store';
import { useAuthStore } from '../store/auth.store';
import { Avatar } from './Avatar';
import { ProfileModal } from './ProfileModal';

export function Sidebar() {
  const { theme, toggleTheme, sidebarView, setSidebarView } = useUIStore();
  const user = useAuthStore((s) => s.user);
  const [profileOpen, setProfileOpen] = useState(false);

  return (
    <>
      <nav className="sidebar" aria-label="Main navigation">
        {/* Logo mark */}
        <div style={{ marginBottom: 8 }}>
          <div
            style={{
              width: 36, height: 36, background: 'var(--accent)',
              borderRadius: 10, display: 'flex', alignItems: 'center',
              justifyContent: 'center', fontWeight: 800, fontSize: 16,
              color: '#fff', letterSpacing: '-0.5px',
            }}
            aria-hidden
          >
            S
          </div>
        </div>

        <div className="divider" style={{ width: '60%', margin: '4px 0 8px' }} />

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

        {/* Settings */}
        <button
          id="sidebar-settings"
          className={`sidebar-btn ${sidebarView === 'settings' ? 'active' : ''}`}
          onClick={() => setSidebarView('settings')}
          aria-label="Settings"
          title="Settings"
        >
          <Settings size={20} />
          <span className="tooltip">Settings</span>
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

        {/* Profile avatar — opens modal */}
        {user && (
          <button
            id="profile-btn"
            className="sidebar-btn"
            onClick={() => setProfileOpen(true)}
            aria-label="Profile"
            title="Profile"
            style={{ padding: 0, width: 44, height: 44 }}
          >
            <Avatar src={user.avatarUrl} username={user.username} size="sm" />
            <span className="tooltip">Profile</span>
          </button>
        )}
      </nav>

      {profileOpen && <ProfileModal onClose={() => setProfileOpen(false)} />}
    </>
  );
}
