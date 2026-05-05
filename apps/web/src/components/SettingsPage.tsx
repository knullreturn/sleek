import React, { useEffect, useRef, useState } from 'react';
import { X, User, Palette, LogOut, ArrowLeft, Camera, Download, Smartphone, Moon } from 'lucide-react';
import { useAuthStore } from '../store/auth.store';
import { useUIStore } from '../store/ui.store';
import { Avatar } from './Avatar';
import { AvatarCropModal } from './AvatarCropModal';
import api from '../lib/api';
import { safeEmit } from '../hooks/useSocket';

type SettingsSection = 'account' | 'appearance' | 'downloads';

// ── Account section ────────────────────────────────────────────────────────────
function AccountSection() {
  const user       = useAuthStore((s) => s.user);
  const token      = useAuthStore((s) => s.token);
  const setAuth    = useAuthStore((s) => s.setAuth);
  const sleepMode  = useUIStore((s) => s.sleepMode);
  const setSleepMode = useUIStore((s) => s.setSleepMode);

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

        {/* ── Sleep Mode ─────────────────────────────────────────────────── */}
        <div style={{
          marginTop: 24,
          padding: '18px 20px',
          background: 'var(--bg-elevated)',
          border: `1px solid ${sleepMode ? 'var(--accent)' : 'var(--border)'}`,
          borderRadius: 'var(--radius-lg)',
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          transition: 'border-color 200ms',
        }}>
          {/* Icon */}
          <div style={{
            width: 40, height: 40, borderRadius: 12, flexShrink: 0,
            background: sleepMode ? 'var(--accent-dim)' : 'var(--bg-input)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'background 200ms',
          }}>
            <Moon size={18} style={{ color: sleepMode ? 'var(--accent)' : 'var(--text-muted)' }} />
          </div>

          {/* Text */}
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 8 }}>
              Sleep Mode
              {sleepMode && (
                <span style={{
                  fontSize: 10, fontWeight: 700, letterSpacing: 0.5,
                  color: 'var(--accent)', background: 'var(--accent-dim)',
                  padding: '2px 7px', borderRadius: 4,
                }}>ACTIVE</span>
              )}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 3, lineHeight: 1.4 }}>
              {sleepMode
                ? 'Peers see 💤 Do Not Disturb instead of Online'
                : 'Mute your online presence — show as Do Not Disturb'}
            </div>
          </div>

          {/* Toggle switch */}
          <button
            id="sleep-mode-toggle"
            role="switch"
            aria-checked={sleepMode}
            onClick={() => {
              const next = !sleepMode;
              setSleepMode(next);
              // Emit to server immediately — peers update within milliseconds
              safeEmit('set_sleep_mode', { enabled: next });
            }}
            style={{
              position: 'relative', flexShrink: 0,
              width: 44, height: 24, borderRadius: 12,
              background: sleepMode ? 'var(--accent)' : 'var(--bg-input)',
              border: `2px solid ${sleepMode ? 'var(--accent)' : 'var(--border)'}`,
              cursor: 'pointer', transition: 'background 200ms, border-color 200ms',
              padding: 0,
            }}
          >
            <span style={{
              position: 'absolute', top: 2,
              left: sleepMode ? 'calc(100% - 20px)' : 2,
              width: 16, height: 16, borderRadius: '50%',
              background: sleepMode ? '#fff' : 'var(--text-muted)',
              transition: 'left 200ms cubic-bezier(0.4,0,0.2,1), background 200ms',
              display: 'block',
            }} />
          </button>
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

// ── Downloads section ──────────────────────────────────────────────────────────────
// Always points to the latest release — never needs updating when a new version ships
const APK_URL = 'https://github.com/knullreturn/sleek/releases/latest/download/sleek.apk';

function DownloadsSection() {
  return (
    <div className="settings-section">
      <h2 className="settings-section-title">Downloads</h2>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 24, lineHeight: 1.6 }}>
        Get the native SLEEK experience on your Android device. Faster, lighter, with instant notifications.
      </p>

      {/* Android card */}
      <div style={{
        background: 'var(--bg-elevated)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: '24px',
        display: 'flex',
        alignItems: 'center',
        gap: 20,
      }}>
        {/* Android icon */}
        <div style={{
          width: 56, height: 56, borderRadius: 16,
          background: 'linear-gradient(135deg, #3ddc84 0%, #2bb870 100%)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0, boxShadow: '0 4px 16px rgba(61,220,132,0.3)',
        }}>
          <Smartphone size={28} color="#fff" />
        </div>

        {/* Info */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 16, color: 'var(--text-primary)' }}>SLEEK for Android</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 3 }}>
            Version 1.0.0 &nbsp;·&nbsp; Android 8.0+
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
            Room-powered · Instant opens · WhatsApp-speed
          </div>
        </div>

        {/* Download button */}
        <a
          href={APK_URL}
          download
          style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '10px 20px', borderRadius: 'var(--radius-md)',
            background: 'var(--accent)', color: '#fff',
            fontSize: 14, fontWeight: 600, textDecoration: 'none',
            transition: 'opacity 150ms', flexShrink: 0,
            boxShadow: '0 2px 12px rgba(var(--accent-rgb), 0.35)',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.85')}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
        >
          <Download size={16} />
          Download APK
        </a>
      </div>

      {/* Install instructions */}
      <div style={{
        marginTop: 20,
        background: 'var(--bg-input)',
        border: '1px solid var(--border-subtle)',
        borderRadius: 'var(--radius-md)',
        padding: '16px 20px',
      }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-secondary)', marginBottom: 10, textTransform: 'uppercase', letterSpacing: 0.8 }}>How to install</div>
        {[
          ['1', 'Tap Download APK above'],
          ['2', 'Open the downloaded file on your phone'],
          ['3', 'Allow “Install unknown apps” if prompted'],
          ['4', 'Tap Install — done!'],
        ].map(([n, text]) => (
          <div key={n} style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 8 }}>
            <div style={{
              width: 20, height: 20, borderRadius: '50%', background: 'var(--accent-dim)',
              color: 'var(--accent)', fontSize: 11, fontWeight: 700,
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>{n}</div>
            <span style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>{text}</span>
          </div>
        ))}
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
    { key: 'downloads',  label: 'Downloads',  icon: <Download size={16} /> },
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
        {section === 'downloads'  && <DownloadsSection />}
      </main>
    </div>
  );
}
