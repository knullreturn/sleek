import { create } from 'zustand';

interface UIState {
  theme: 'dark' | 'light';
  searchOpen: boolean;
  sidebarView: 'chats' | 'settings';
  toggleTheme: () => void;
  setSearchOpen: (open: boolean) => void;
  setSidebarView: (view: 'chats' | 'settings') => void;
}

export const useUIStore = create<UIState>((set) => ({
  theme: 'dark',
  searchOpen: false,
  sidebarView: 'chats',

  toggleTheme: () =>
    set((state) => {
      const next = state.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      return { theme: next };
    }),

  setSearchOpen: (open) => set({ searchOpen: open }),
  setSidebarView: (view) => set({ sidebarView: view }),
}));
