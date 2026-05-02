import React, { useState } from 'react';
import api from '../lib/api';
import { useAuthStore } from '../store/auth.store';

declare global {
  interface Window {
    google?: any;
  }
}

export function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setAuth = useAuthStore((s) => s.setAuth);

  const handleGoogleLogin = () => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    if (!clientId || clientId === 'placeholder') {
      setError('Google OAuth is not configured yet. Add VITE_GOOGLE_CLIENT_ID to .env');
      return;
    }

    setLoading(true);
    setError(null);

    // Use Google Identity Services popup
    window.google?.accounts.id.initialize({
      client_id: clientId,
      callback: async ({ credential }: { credential: string }) => {
        try {
          const res = await api.post('/auth/google', { idToken: credential });
          setAuth(res.data.token, res.data.user);
        } catch (err: any) {
          setError(err.response?.data?.message || 'Authentication failed. Please try again.');
          setLoading(false);
        }
      },
      auto_select: false,
    });

    window.google?.accounts.id.prompt((notification: any) => {
      if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
        // Fallback: use renderButton
        setLoading(false);
      }
    });
  };

  return (
    <div className="login-page">
      {/* Background gradient orb */}
      <div
        aria-hidden
        style={{
          position: 'fixed',
          top: '20%',
          left: '50%',
          transform: 'translateX(-50%)',
          width: 600,
          height: 600,
          background: 'radial-gradient(circle, rgba(124,92,252,0.08) 0%, transparent 70%)',
          pointerEvents: 'none',
          borderRadius: '50%',
        }}
      />

      <div className="login-card animate-slide-up">
        {/* Logo */}
        <div className="login-logo">
          <div className="login-logo-mark">S</div>
          <span className="login-logo-text">SLEEK</span>
        </div>

        {/* Tagline */}
        <div className="login-tagline">
          <strong style={{ color: 'var(--text-secondary)' }}>Fast. Clean. Effortless.</strong>
          <br />
          A modern chat experience for everyone.
        </div>

        {/* Google button */}
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <button
            id="google-login-btn"
            className="google-btn"
            onClick={handleGoogleLogin}
            disabled={loading}
            aria-label="Continue with Google"
          >
            {/* Google icon */}
            <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden>
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            {loading ? 'Signing in…' : 'Continue with Google'}
          </button>

          {error && (
            <div
              style={{
                padding: '10px 14px',
                background: 'rgba(239,68,68,0.1)',
                border: '1px solid rgba(239,68,68,0.3)',
                borderRadius: 'var(--radius-md)',
                color: '#ef4444',
                fontSize: 12,
                lineHeight: 1.5,
              }}
              role="alert"
            >
              {error}
            </div>
          )}
        </div>

        <p style={{ fontSize: 11, color: 'var(--text-muted)', textAlign: 'center' }}>
          By continuing, you agree to SLEEK's Terms of Service
          <br />and Privacy Policy.
        </p>
      </div>
    </div>
  );
}
