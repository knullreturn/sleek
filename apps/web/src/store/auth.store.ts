import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface User {
  id: string;
  username: string | null;   // null until onboarding complete
  tag: string;               // 7-char unique ID e.g. 483921A
  handle: string;            // same as tag
  avatarUrl: string | null;
  createdAt: string;
  needsOnboarding?: boolean;
}

interface AuthState {
  token: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
      isAuthenticated: () => !!get().token && !!get().user,
    }),
    {
      name: 'sleek-auth-v2',
      // ✅ Security: sessionStorage clears on tab/browser close — limits XSS token theft window.
      // Trade-off: users must re-login per browser session (acceptable for a chat app).
      storage: {
        getItem:    (k) => { const v = sessionStorage.getItem(k); return v ? JSON.parse(v) : null; },
        setItem:    (k, v) => sessionStorage.setItem(k, JSON.stringify(v)),
        removeItem: (k) => sessionStorage.removeItem(k),
      },
    }
  )
);
