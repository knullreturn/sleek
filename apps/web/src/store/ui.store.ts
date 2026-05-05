import { create } from 'zustand';

interface UIState {
  theme: 'dark' | 'light';
  searchOpen: boolean;
  sidebarView: 'chats' | 'settings';
  chatListOpen: boolean;
  sleepMode: boolean;
  toggleTheme: () => void;
  setSearchOpen: (open: boolean) => void;
  setSidebarView: (view: 'chats' | 'settings') => void;
  toggleChatList: () => void;
  setSleepMode: (enabled: boolean) => void;
}

// Read sleep mode from localStorage on startup so it persists across sessions
// (same as Android DataStore — survives app restarts)
const storedSleep = localStorage.getItem('sleek-sleep-mode') === 'true';

export const useUIStore = create<UIState>((set) => ({
  theme: 'dark',
  searchOpen: false,
  sidebarView: 'chats',
  chatListOpen: true,
  sleepMode: storedSleep,

  toggleTheme: () =>
    set((state) => {
      const next = state.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      return { theme: next };
    }),

  setSearchOpen: (open) => set({ searchOpen: open }),
  setSidebarView: (view) => set({ sidebarView: view }),
  toggleChatList: () => set((state) => ({ chatListOpen: !state.chatListOpen })),

  setSleepMode: (enabled) => {
    // Persist locally — survives tab refresh (same behaviour as Android DataStore)
    localStorage.setItem('sleek-sleep-mode', String(enabled));
    set({ sleepMode: enabled });
    // Caller (AccountSection) is responsible for emitting set_sleep_mode to server
    // so we don't couple the store to the socket directly
  },
}));
