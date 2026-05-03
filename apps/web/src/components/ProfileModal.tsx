import React from 'react';
import { useAuthStore } from '../store/auth.store';
import { Avatar } from './Avatar';
import { LogOut, X, Settings } from 'lucide-react';

interface ProfileModalProps {
  onClose: () => void;
}

export function ProfileModal({ onClose }: ProfileModalProps) {
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const handleLogout = () => {
    clearAuth();
    onClose();
  };

  return (
    <>
      {/* Backdrop */}
      <div
        style={{ position: 'fixed', inset: 0, zIndex: 140 }}
        onClick={onClose}
        aria-hidden
      />

      {/* Popover — anchored bottom-left above avatar */}
      <div
        role="dialog"
        aria-label="Profile"
        style={{
          position: 'fixed',
          bottom: 'calc(var(--sidebar-width) - 16px)',
          left: 8,
          width: 260,
          background: 'var(--bg-elevated)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-xl)',
          boxShadow: 'var(--shadow-lg)',
          zIndex: 150,
          overflow: 'hidden',
          animation: 'slideUp var(--transition-base) forwards',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '20px 20px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 14,
            borderBottom: '1px solid var(--border-subtle)',
          }}
        >
          <Avatar src={user?.avatarUrl} username={user?.username || '?'} size="lg" />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 600, fontSize: 15, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {user?.username}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
              {user?.handle}
            </div>
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <X size={16} />
          </button>
        </div>

        {/* Actions */}
        <div style={{ padding: 8 }}>
          <button
            style={{
              width: '100%', display: 'flex', alignItems: 'center', gap: 12,
              padding: '10px 12px', borderRadius: 'var(--radius-md)',
              background: 'transparent', border: 'none', cursor: 'pointer',
              color: 'var(--text-secondary)', fontSize: 14, fontFamily: 'inherit',
              transition: 'background var(--transition-fast)',
            }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-hover)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          >
            <Settings size={16} />
            Settings
            <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--text-muted)' }}>Soon</span>
          </button>

          <div style={{ height: 1, background: 'var(--border-subtle)', margin: '4px 0' }} />

          <button
            id="logout-btn"
            onClick={handleLogout}
            style={{
              width: '100%', display: 'flex', alignItems: 'center', gap: 12,
              padding: '10px 12px', borderRadius: 'var(--radius-md)',
              background: 'transparent', border: 'none', cursor: 'pointer',
              color: 'var(--danger)', fontSize: 14, fontFamily: 'inherit',
              transition: 'background var(--transition-fast)',
            }}
            onMouseEnter={e => (e.currentTarget.style.background = 'rgba(239,68,68,0.08)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
            aria-label="Sign out"
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </div>
    </>
  );
}
