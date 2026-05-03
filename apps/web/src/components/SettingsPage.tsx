import React, { useEffect, useState } from 'react';
import { X, User, Palette, LogOut } from 'lucide-react';
import { useAuthStore } from '../store/auth.store';
import { useUIStore } from '../store/ui.store';
import { Avatar } from './Avatar';

type SettingsSection = 'account' | 'appearance';

interface SettingsPageProps {
  onClose: () => void;
}

// ── Account section ────────────────────────────────────────────────────────────
function AccountSection() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="settings-section">
      <h2 className="settings-section-title">Account</h2>

      {/* Profile card */}
      <div className="settings-profile-card">
        <div style={{ position: 'relative', display: 'inline-block' }}>
          <Avatar src={user?.avatarUrl} username={user?.username ?? ''} size="lg" />
          <div
            style={{
              position: 'absolute', inset: 0, borderRadius: '50%',
              background: 'rgba(0,0,0,0.45)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              opacity: 0, transition: 'opacity 150ms',
              cursor: 'pointer', fontSize: 11, color: '#fff', fontWeight: 600,
            }}
            className="avatar-overlay"
          >
            Edit
          </div>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 18, color: 'var(--text-primary)' }}>
            {user?.username || 'No username'}
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
            ID: <span style={{ fontFamily: 'monospace', letterSpacing: 1, color: 'var(--accent)' }}>{user?.tag}</span>
          </div>
        </div>
      </div>

      {/* Fields */}
      <div className="settings-field-group">
        <label className="settings-label">Username</label>
        <div className="settings-field-value">{user?.username || '—'}</div>
      </div>

      <div className="settings-field-group">
        <label className="settings-label">Unique ID</label>
        <div className="settings-field-value" style={{ fontFamily: 'monospace', letterSpacing: 1 }}>
          {user?.tag}
        </div>
      </div>
    </div>
  );
}

// ── Appearance section ─────────────────────────────────────────────────────────
function AppearanceSection() {
  const { theme, toggleTheme } = useUIStore();

  return (
    <div className="settings-section">
      <h2 className="settings-section-title">Appearance</h2>

      <div className="settings-field-group">
        <label className="settings-label">Theme</label>
        <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
          {(['dark', 'light'] as const).map((t) => (
            <button
              key={t}
              onClick={() => { if (theme !== t) toggleTheme(); }}
              style={{
                padding: '10px 20px',
                borderRadius: 'var(--radius-md)',
                border: `2px solid ${theme === t ? 'var(--accent)' : 'var(--border)'}`,
                background: theme === t ? 'var(--accent-dim)' : 'var(--bg-elevated)',
                color: theme === t ? 'var(--accent)' : 'var(--text-secondary)',
                fontFamily: 'inherit',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
                textTransform: 'capitalize',
                transition: 'all 150ms',
              }}
            >
              {t === 'dark' ? '🌑 Dark' : '☀️ Light'}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Main Settings Overlay ──────────────────────────────────────────────────────
export function SettingsPage({ onClose }: SettingsPageProps) {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [section, setSection] = useState<SettingsSection>('account');
  const [closing, setClosing] = useState(false);

  // Close on Escape key
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') handleClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleClose = () => {
    setClosing(true);
    setTimeout(onClose, 220);
  };

  const handleSignOut = () => {
    clearAuth();
    onClose();
  };

  const navItems: { key: SettingsSection; label: string; icon: React.ReactNode }[] = [
    { key: 'account',    label: 'Account',    icon: <User size={16} /> },
    { key: 'appearance', label: 'Appearance', icon: <Palette size={16} /> },
  ];

  return (
    <div className={`settings-overlay ${closing ? 'settings-closing' : ''}`} onClick={handleClose}>
      {/* Panel wrapper — stops click propagation */}
      <div className="settings-panel-wrap" onClick={(e) => e.stopPropagation()}>

        {/* Left nav */}
        <nav className={`settings-nav ${closing ? 'settings-nav-out' : ''}`}>
          <div style={{ padding: '28px 16px 16px', borderBottom: '1px solid var(--border-subtle)' }}>
            <span style={{ fontWeight: 700, fontSize: 12, letterSpacing: 1, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
              Settings
            </span>
          </div>

          <div style={{ padding: '8px 8px', flex: 1 }}>
            {navItems.map(({ key, label, icon }) => (
              <button
                key={key}
                className={`settings-nav-item ${section === key ? 'active' : ''}`}
                onClick={() => setSection(key)}
              >
                {icon}
                {label}
              </button>
            ))}
          </div>

          {/* Sign out — bottom */}
          <div style={{ padding: '8px', borderTop: '1px solid var(--border-subtle)' }}>
            <button className="settings-nav-item danger" onClick={handleSignOut}>
              <LogOut size={16} />
              Sign out
            </button>
          </div>
        </nav>

        {/* Right content */}
        <div className={`settings-content ${closing ? 'settings-content-out' : ''}`}>
          {/* Close button */}
          <button className="settings-close-btn" onClick={handleClose} aria-label="Close settings">
            <X size={18} />
          </button>

          {section === 'account'    && <AccountSection />}
          {section === 'appearance' && <AppearanceSection />}
        </div>
      </div>
    </div>
  );
}
