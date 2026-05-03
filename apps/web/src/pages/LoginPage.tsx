import React, { useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../store/auth.store';
import api from '../lib/api';

declare global {
  interface Window {
    google?: any;
  }
}

export function LoginPage() {
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const googleBtnRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    if (!clientId || !window.google) return;

    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: async (response: { credential: string }) => {
        setLoading(true);
        setError('');
        try {
          const res = await api.post('/auth/google', { idToken: response.credential });
          setAuth(res.data.token, res.data.user);
        } catch {
          setError('Sign in failed. Please try again.');
        } finally {
          setLoading(false);
        }
      },
    });

    window.google.accounts.id.renderButton(googleBtnRef.current!, {
      type: 'standard',
      theme: 'filled_black',
      size: 'large',
      shape: 'pill',
      text: 'continue_with',
      width: 280,
    });
  }, [setAuth]);

  return (
    <div className="login-page">
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
        }}
      >
        {/* Big ghost logo */}
        <img
          src="/landing.png"
          alt="SLEEK"
          style={{
            width: 180,
            height: 180,
            objectFit: 'contain',
            marginBottom: 8,
            filter: 'drop-shadow(0 0 40px rgba(124,92,252,0.35))',
          }}
        />

        {/* App name */}
        <h1
          style={{
            fontSize: 48,
            fontWeight: 800,
            letterSpacing: '-2px',
            color: 'var(--text-primary)',
            margin: 0,
            lineHeight: 1,
          }}
        >
          SLEEK
        </h1>

        {/* Google sign-in button */}
        {!loading ? (
          <div ref={googleBtnRef} id="google-signin-btn" />
        ) : (
          <div
            style={{
              width: 280,
              height: 44,
              borderRadius: 9999,
              background: 'var(--bg-elevated)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-muted)',
              fontSize: 14,
              gap: 8,
            }}
          >
            <span
              style={{
                width: 16, height: 16, borderRadius: '50%',
                border: '2px solid var(--accent)',
                borderTopColor: 'transparent',
                animation: 'spin 0.7s linear infinite',
                display: 'inline-block',
              }}
            />
            Signing in…
          </div>
        )}

        {error && (
          <p style={{ marginTop: 16, color: 'var(--danger)', fontSize: 13 }}>{error}</p>
        )}
      </div>

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}
