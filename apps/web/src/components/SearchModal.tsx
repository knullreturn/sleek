import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../lib/api';
import { Avatar } from './Avatar';
import { useChatStore } from '../store/chat.store';
import { useUIStore } from '../store/ui.store';
import { Search, X } from 'lucide-react';

function useDebounce<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

export function SearchModal() {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);
  const inputRef = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();

  const setSearchOpen = useUIStore((s) => s.setSearchOpen);
  const { setActiveChatId, upsertChat } = useChatStore();

  useEffect(() => { inputRef.current?.focus(); }, []);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setSearchOpen(false); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  const searchQuery = useQuery({
    queryKey: ['user-search', debouncedQuery],
    queryFn: async () => {
      if (!debouncedQuery.trim()) return [];
      const res = await api.get('/users/search', { params: { q: debouncedQuery } });
      return res.data as any[];
    },
    enabled: debouncedQuery.length >= 1,
  });

  const createChat = useMutation({
    mutationFn: async (targetUserId: string) => {
      const res = await api.post('/chats', { targetUserId });
      return res.data;
    },
    onSuccess: (chat) => {
      upsertChat(chat);
      setActiveChatId(chat.id);
      qc.invalidateQueries({ queryKey: ['chats'] });
      setSearchOpen(false);
    },
  });

  const handleSelect = useCallback((user: any) => {
    createChat.mutate(user.id);
  }, []);

  const results = searchQuery.data || [];

  return (
    <div
      className="modal-overlay"
      onClick={(e) => { if (e.target === e.currentTarget) setSearchOpen(false); }}
      role="dialog"
      aria-modal
      aria-label="Search users"
    >
      <div className="modal">
        <div style={{ position: 'relative' }}>
          <Search
            size={16}
            style={{
              position: 'absolute', left: 20, top: '50%', transform: 'translateY(-50%)',
              color: 'var(--text-muted)', pointerEvents: 'none',
            }}
          />
          <input
            ref={inputRef}
            id="search-input"
            className="modal-search-input"
            style={{ paddingLeft: 44 }}
            placeholder="Search by username or username#1234"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              style={{
                position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)',
                background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)',
                display: 'flex', alignItems: 'center',
              }}
              aria-label="Clear search"
            >
              <X size={14} />
            </button>
          )}
        </div>

        <div className="modal-results">
          {searchQuery.isLoading && (
            <div className="search-empty">Searching…</div>
          )}

          {!searchQuery.isLoading && debouncedQuery && results.length === 0 && (
            <div className="search-empty">No users found for "{debouncedQuery}"</div>
          )}

          {!debouncedQuery && (
            <div className="search-empty" style={{ paddingTop: 24 }}>
              Type a username to find people
            </div>
          )}

          {results.map((u) => (
            <div
              key={u.id}
              id={`search-result-${u.id}`}
              className="search-result-item"
              onClick={() => handleSelect(u)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && handleSelect(u)}
            >
              <Avatar src={u.avatarUrl} username={u.username} size="md" />
              <div>
                <div className="search-result-name">{u.username}</div>
                <div className="search-result-handle">{u.handle}</div>
              </div>
              {createChat.isPending && (
                <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--text-muted)' }}>…</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
