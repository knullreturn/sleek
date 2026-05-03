import React, { useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../store/auth.store';
import api from '../lib/api';

declare global {
  interface Window { google?: any; }
}

const GOOGLE_SCRIPT_ID = 'google-gsi-script';

function loadGoogleScript(): Promise<void> {
  return new Promise((resolve) => {
    if (window.google?.accounts) { resolve(); return; }
    if (document.getElementById(GOOGLE_SCRIPT_ID)) {
      // Script tag already added — wait for it
      const wait = () => window.google?.accounts ? resolve() : setTimeout(wait, 100);
      wait();
      return;
    }
    const script = document.createElement('script');
    script.id = GOOGLE_SCRIPT_ID;
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    document.head.appendChild(script);
  });
}

export function LoginPage() {
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const googleBtnRef = useRef<HTMLDivElement>(null);
  const initialised = useRef(false);

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    if (!clientId) {
      setError('Google Client ID not configured.');
      return;
    }

    loadGoogleScript().then(() => {
      if (initialised.current) return;
      initialised.current = true;

      window.google.accounts.id.initialize({
        client_id: clientId,
        ux_mode: 'popup',
        callback: async (response: { credential: string }) => {
          setLoading(true);
          setError('');
          try {
            const res = await api.post('/auth/google', { idToken: response.credential });
            setAuth(res.data.token, res.data.user);
          } catch (err: any) {
            const msg = err?.response?.data?.message || err?.message || 'Sign in failed.';
            setError(msg);
          } finally {
            setLoading(false);
          }
        },
      });

      // Render the button — keep div always mounted so this only runs once
      if (googleBtnRef.current) {
        window.google.accounts.id.renderButton(googleBtnRef.current, {
          type: 'standard',
          theme: 'filled_black',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          width: 280,
        });
      }
    });
  }, [setAuth]);

  return (
    <div className="login-page">
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>

        {/* Ghost logo */}
        <img
          src="/landing.png"
          alt="SLEEK"
          style={{ width: 180, height: 180, objectFit: 'contain', marginBottom: 8 }}
        />

        {/* App name */}
        <h1 style={{ fontSize: 48, fontWeight: 800, letterSpacing: '-2px', color: 'var(--text-primary)', margin: '0 0 32px', lineHeight: 1 }}>
          SLEEK
        </h1>

        {/* Google button — always mounted, hidden behind spinner when loading */}
        <div style={{ position: 'relative', minWidth: 280, minHeight: 44 }}>
          {/* Button div is ALWAYS in DOM so GIS widget persists after errors */}
          <div
            ref={googleBtnRef}
            id="google-signin-btn"
            style={{ visibility: loading ? 'hidden' : 'visible' }}
          />
          {loading && (
            <div
              style={{
                position: 'absolute', inset: 0,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: 'var(--bg-elevated)', borderRadius: 9999,
                color: 'var(--text-muted)', fontSize: 14, gap: 8,
              }}
            >
              <span style={{
                width: 16, height: 16, borderRadius: '50%',
                border: '2px solid var(--accent)', borderTopColor: 'transparent',
                animation: 'spin 0.7s linear infinite', display: 'inline-block',
              }} />
              Signing in…
            </div>
          )}
        </div>

        {error && (
          <p style={{ marginTop: 16, color: 'var(--danger)', fontSize: 13, textAlign: 'center', maxWidth: 280 }}>
            {error}
          </p>
        )}
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
