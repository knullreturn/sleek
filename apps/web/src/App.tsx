import React, { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from './pages/LoginPage';
import { ChatPage } from './pages/ChatPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { useAuthStore } from './store/auth.store';
import api from './lib/api';

export default function App() {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setAuth = useAuthStore((s) => s.setAuth);

  const isAuthenticated = !!token && !!user;
  const needsOnboarding = isAuthenticated && !user?.username;

  // On startup: verify session is still valid against server
  // This also refreshes user data (picks up username after onboarding)
  useEffect(() => {
    if (!token) return;
    api.get('/users/me')
      .then((res) => {
        // Refresh user in store with latest server data
        setAuth(token, res.data);
      })
      .catch(() => {
        // Token invalid or user not found — clear and force re-login
        clearAuth();
      });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // only on mount

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        path="/onboarding"
        element={
          !isAuthenticated ? <Navigate to="/login" replace />
          : !needsOnboarding ? <Navigate to="/" replace />
          : <OnboardingPage />
        }
      />
      <Route
        path="/*"
        element={
          !isAuthenticated ? <Navigate to="/login" replace />
          : needsOnboarding ? <Navigate to="/onboarding" replace />
          : <ChatPage />
        }
      />
    </Routes>
  );
}
