import React, { useState } from 'react';
import { useAuthStore } from '../store/auth.store';
import api from '../lib/api';

export function OnboardingPage() {
  const token = useAuthStore((s) => s.token);
  const setAuth = useAuthStore((s) => s.setAuth);
  const [username, setUsername] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const isValid = /^[a-zA-Z0-9_]{2,30}$/.test(username);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid || loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await api.post('/auth/onboard', { username });
      setAuth(res.data.token, res.data.user);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Something went wrong.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%', maxWidth: 360, padding: '0 24px' }}>

        <img src="/landing.png" alt="SLEEK" style={{ width: 100, height: 100, objectFit: 'contain', marginBottom: 24 }} />

        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: '-1px', color: 'var(--text-primary)', margin: '0 0 8px', lineHeight: 1 }}>
          Welcome to SLEEK
        </h1>
        <p style={{ fontSize: 14, color: 'var(--text-muted)', margin: '0 0 36px', textAlign: 'center' }}>
          Choose a username. This is how others will find you.
        </p>

        <form onSubmit={handleSubmit} style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ position: 'relative' }}>
            <input
              id="username-input"
              type="text"
              value={username}
              onChange={(e) => { setUsername(e.target.value); setError(''); }}
              placeholder="e.g. john_doe"
              autoFocus
              maxLength={30}
              autoComplete="off"
              style={{
                width: '100%',
                padding: '12px 16px',
                background: 'var(--bg-elevated)',
                border: `1px solid ${error ? 'var(--danger)' : username && isValid ? 'var(--accent)' : 'var(--border)'}`,
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-primary)',
                fontSize: 15,
                fontFamily: 'inherit',
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 150ms',
              }}
            />
          </div>

          <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0, lineHeight: 1.5 }}>
            2–30 characters · letters, numbers and underscores only
          </p>

          {error && (
            <p style={{ fontSize: 13, color: 'var(--danger)', margin: 0 }}>{error}</p>
          )}

          <button
            type="submit"
            id="onboard-submit-btn"
            disabled={!isValid || loading}
            style={{
              marginTop: 8,
              padding: '12px',
              borderRadius: 'var(--radius-md)',
              background: isValid ? 'var(--accent)' : 'var(--bg-elevated)',
              border: 'none',
              color: isValid ? '#fff' : 'var(--text-muted)',
              fontSize: 15,
              fontWeight: 600,
              fontFamily: 'inherit',
              cursor: isValid && !loading ? 'pointer' : 'not-allowed',
              transition: 'background 200ms, color 200ms',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
            }}
          >
            {loading ? (
              <>
                <span style={{
                  width: 16, height: 16, borderRadius: '50%',
                  border: '2px solid rgba(255,255,255,0.4)', borderTopColor: '#fff',
                  animation: 'spin 0.7s linear infinite', display: 'inline-block',
                }} />
                Setting up…
              </>
            ) : 'Continue'}
          </button>
        </form>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
