import React, { useEffect, useState, useRef } from 'react';
import { X, Pin, ArrowUpRight, PinOff } from 'lucide-react';
import { Avatar } from './Avatar';
import api from '../lib/api';
import { getSocket } from '../hooks/useSocket';
import { formatMessageTime } from '../lib/utils';

interface PinnedPanelProps {
  chatId:        string;
  onClose:       () => void;
  onJump:        (messageId: string) => void;
}

export function PinnedPanel({ chatId, onClose, onJump }: PinnedPanelProps) {
  const [pins, setPins]   = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const panelRef = useRef<HTMLDivElement>(null);

  // Fetch pinned messages
  useEffect(() => {
    setLoading(true);
    api.get(`/chats/${chatId}/pins`)
      .then((r) => setPins(r.data.pins))
      .catch(() => setPins([]))
      .finally(() => setLoading(false));
  }, [chatId]);

  // Close on Escape
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, [onClose]);

  // Close on outside click
  useEffect(() => {
    const id = setTimeout(() => {
      const h = (e: MouseEvent) => {
        if (panelRef.current && !panelRef.current.contains(e.target as Node)) onClose();
      };
      document.addEventListener('mousedown', h);
      return () => document.removeEventListener('mousedown', h);
    }, 50);
    return () => clearTimeout(id);
  }, [onClose]);

  const handleUnpin = (messageId: string) => {
    const socket = getSocket();
    socket?.emit('unpin_message', { messageId, chatId });
    // Optimistic update
    setPins((prev) => prev.filter((p) => p.id !== messageId));
  };

  return (
    <div className="pinned-panel" ref={panelRef} role="dialog" aria-label="Pinned messages">
      {/* Header */}
      <div className="pinned-panel-header">
        <div className="pinned-panel-title">
          <Pin size={15} className="pinned-panel-icon" />
          <span>Pinned Messages</span>
          {pins.length > 0 && <span className="pinned-count">{pins.length}</span>}
        </div>
        <button className="icon-btn" onClick={onClose} aria-label="Close pinned panel">
          <X size={16} />
        </button>
      </div>

      {/* Body */}
      <div className="pinned-panel-body">
        {loading && (
          <div className="pinned-loading">
            {[1,2,3].map((i) => (
              <div key={i} className="pinned-skeleton" style={{ animationDelay: `${i * 120}ms` }} />
            ))}
          </div>
        )}

        {!loading && pins.length === 0 && (
          <div className="pinned-empty">
            <Pin size={28} opacity={0.2} />
            <p>No pinned messages yet</p>
            <span>Right-click any message and pin it</span>
          </div>
        )}

        {!loading && pins.map((pin, i) => (
          <div
            key={pin.id}
            className="pinned-card"
            style={{ '--card-delay': `${i * 60}ms` } as React.CSSProperties}
          >
            {/* Accent bar */}
            <div className="pinned-card-bar" />

            <div className="pinned-card-inner">
              {/* Sender row */}
              <div className="pinned-card-sender">
                <Avatar
                  src={pin.sender?.avatarUrl}
                  username={pin.sender?.username || '?'}
                  size="xs"
                />
                <span className="pinned-card-name">{pin.sender?.username}</span>
                <span className="pinned-card-time">{formatMessageTime(pin.createdAt)}</span>
              </div>
              {pin.pinnedBy && (
                <div className="pinned-card-pinby">
                  📌 Pinned by <strong>{pin.pinnedBy.username}</strong>
                </div>
              )}

              {/* Content */}
              <p className="pinned-card-content">{pin.content}</p>

              {/* Actions */}
              <div className="pinned-card-actions">
                <button
                  className="pinned-action-btn"
                  onClick={() => { onJump(pin.id); onClose(); }}
                  title="Jump to message"
                >
                  <ArrowUpRight size={13} />
                  Jump to message
                </button>
                <button
                  className="pinned-action-btn danger"
                  onClick={() => handleUnpin(pin.id)}
                  title="Unpin"
                >
                  <PinOff size={13} />
                  Unpin
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
