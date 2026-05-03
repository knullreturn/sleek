import React, { useEffect, useRef, useState } from 'react';
import { X, User, Palette, LogOut, ArrowLeft, Camera } from 'lucide-react';
import { useAuthStore } from '../store/auth.store';
import { useUIStore } from '../store/ui.store';
import { Avatar } from './Avatar';
import { AvatarCropModal } from './AvatarCropModal';
import api from '../lib/api';

type SettingsSection = 'account' | 'appearance';

// ── Account section ────────────────────────────────────────────────────────────
function AccountSection() {
  const user    = useAuthStore((s) => s.user);
  const token   = useAuthStore((s) => s.token);
  const setAuth = useAuthStore((s) => s.setAuth);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadError,  setUploadError]  = useState('');

  const handleAvatarClick = () => { fileInputRef.current?.click(); };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    if (!f.type.startsWith('image/')) { setUploadError('Please select an image file.'); return; }
    setUploadError('');
    setSelectedFile(f);
    // reset input so same file can be re-selected
    e.target.value = '';
  };

  const handleCropConfirm = async (blob: Blob) => {
    setUploadError('');
    try {
      // 1. Get Cloudinary signed params from our server
      const { data: signData } = await api.get('/users/me/avatar/sign');
      const { signature, timestamp, apiKey, cloudName, folder } = signData;

      // 2. Upload directly to Cloudinary
      const formData = new FormData();
      formData.append('file', blob, 'avatar.webp');
      formData.append('api_key',   apiKey);
      formData.append('timestamp', timestamp);
      formData.append('signature', signature);
      formData.append('folder',    folder);

      const uploadRes = await fetch(
        `https://api.cloudinary.com/v1_1/${cloudName}/image/upload`,
        { method: 'POST', body: formData }
      );
      if (!uploadRes.ok) throw new Error('Cloudinary upload failed');
      const uploadData = await uploadRes.json();

      // 3. Save URL to our server + refresh auth store
      const { data: updatedUser } = await api.patch('/users/me/avatar', { avatarUrl: uploadData.secure_url });
      if (token) setAuth(token, updatedUser);

    } catch (err: any) {
      setUploadError(err?.message || 'Upload failed. Please try again.');
    }
    setSelectedFile(null);
  };

  return (
    <>
      <div className="settings-section">
        <h2 className="settings-section-title">Account</h2>

        {/* Profile card with clickable avatar */}
        <div className="settings-profile-card">
          <div
            className="settings-avatar-wrap"
            onClick={handleAvatarClick}
            title="Change photo"
          >
            <Avatar src={user?.avatarUrl} username={user?.username ?? ''} size="lg" />
            <div className="settings-avatar-overlay">
              <Camera size={18} />
              <span style={{ fontSize: 10, fontWeight: 600, marginTop: 2 }}>Change</span>
            </div>
          </div>

          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontSize: 20, color: 'var(--text-primary)' }}>
              {user?.username || 'No username set'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>
              ID: <span style={{ fontFamily: 'monospace', letterSpacing: 1.5, color: 'var(--accent)' }}>{user?.tag}</span>
            </div>
          </div>
        </div>

        {uploadError && (
          <p style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 16 }}>{uploadError}</p>
        )}

        <div className="settings-field-group">
          <label className="settings-label">Username</label>
          <div className="settings-field-value">{user?.username || '—'}</div>
        </div>
        <div className="settings-field-group">
          <label className="settings-label">Unique ID</label>
          <div className="settings-field-value" style={{ fontFamily: 'monospace', letterSpacing: 1.5 }}>{user?.tag}</div>
        </div>
      </div>

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />

      {/* Crop modal */}
      {selectedFile && (
        <AvatarCropModal
          file={selectedFile}
          onConfirm={handleCropConfirm}
          onCancel={() => setSelectedFile(null)}
        />
      )}
    </>
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
                padding: '10px 24px', borderRadius: 'var(--radius-md)',
                border: `2px solid ${theme === t ? 'var(--accent)' : 'var(--border)'}`,
                background: theme === t ? 'var(--accent-dim)' : 'var(--bg-elevated)',
                color: theme === t ? 'var(--accent)' : 'var(--text-secondary)',
                fontFamily: 'inherit', fontSize: 14, fontWeight: 600,
                cursor: 'pointer', textTransform: 'capitalize', transition: 'all 150ms',
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

// ── Main Settings Page ─────────────────────────────────────────────────────────
interface SettingsPageProps { onClose: () => void; }

export function SettingsPage({ onClose }: SettingsPageProps) {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [section, setSection] = useState<SettingsSection>('account');
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') handleClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleClose = () => { setClosing(true); setTimeout(onClose, 260); };
  const handleSignOut = () => { clearAuth(); onClose(); };

  const navItems: { key: SettingsSection; label: string; icon: React.ReactNode }[] = [
    { key: 'account',    label: 'Account',    icon: <User size={16} /> },
    { key: 'appearance', label: 'Appearance', icon: <Palette size={16} /> },
  ];

  return (
    <div className={`settings-fullpage ${closing ? 'settings-fullpage-out' : ''}`}>

      {/* Left nav */}
      <nav className={`settings-nav ${closing ? 'settings-nav-out' : ''}`}>
        <button className="settings-back-btn" onClick={handleClose}>
          <ArrowLeft size={16} /><span>Back</span>
        </button>

        <div style={{ padding: '8px', flex: 1 }}>
          <p style={{ fontSize: 11, fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase', color: 'var(--text-muted)', padding: '4px 12px 8px' }}>
            Settings
          </p>
          {navItems.map(({ key, label, icon }) => (
            <button key={key} className={`settings-nav-item ${section === key ? 'active' : ''}`} onClick={() => setSection(key)}>
              {icon}{label}
            </button>
          ))}
        </div>

        <div style={{ padding: '8px', borderTop: '1px solid var(--border-subtle)' }}>
          <button className="settings-nav-item danger" onClick={handleSignOut}>
            <LogOut size={16} />Sign out
          </button>
        </div>
      </nav>

      {/* Right content */}
      <main className={`settings-content ${closing ? 'settings-content-out' : ''}`}>
        <button className="settings-close-btn" onClick={handleClose} aria-label="Close settings">
          <X size={18} />
        </button>
        {section === 'account'    && <AccountSection />}
        {section === 'appearance' && <AppearanceSection />}
      </main>
    </div>
  );
}
