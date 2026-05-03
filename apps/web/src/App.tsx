import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from './pages/LoginPage';
import { ChatPage } from './pages/ChatPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { useAuthStore } from './store/auth.store';

export default function App() {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = !!token && !!user;
  const needsOnboarding = isAuthenticated && !user?.username;

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
