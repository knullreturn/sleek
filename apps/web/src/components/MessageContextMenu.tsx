import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Copy, Reply, Pencil, Pin, Trash2, Check } from 'lucide-react';

interface MessageContextMenuProps {
  x:        number;
  y:        number;
  isOwn:    boolean;
  isPinned: boolean;
  content:  string;
  onClose:  () => void;
  onReply:  () => void;
  onEdit:   () => void;
  onPin:    () => void;
}

const BASE_ITEMS = [
  { id: 'copy',   label: 'Copy',   icon: Copy,   ownOnly: false, danger: false },
  { id: 'reply',  label: 'Reply',  icon: Reply,  ownOnly: false, danger: false },
  { id: 'pin',    label: 'Pin',    icon: Pin,    ownOnly: false, danger: false },
  { id: 'edit',   label: 'Edit',   icon: Pencil, ownOnly: true,  danger: false },
  { id: 'delete', label: 'Delete', icon: Trash2, ownOnly: true,  danger: true  },
];

export function MessageContextMenu({ x, y, isOwn, isPinned, content, onClose, onReply, onEdit, onPin }: MessageContextMenuProps) {
  const menuRef       = useRef<HTMLDivElement>(null);
  const [copied, setCopied] = useState(false);

  // Close on outside click
  useEffect(() => {
    const id = setTimeout(() => {
      const handler = (e: MouseEvent) => {
        if (menuRef.current && !menuRef.current.contains(e.target as Node)) onClose();
      };
      document.addEventListener('mousedown', handler);
      return () => document.removeEventListener('mousedown', handler);
    }, 10);
    return () => clearTimeout(id);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);

  // Smart positioning
  const menuW = 200;
  const menuH = BASE_ITEMS.filter(i => !i.ownOnly || isOwn).length * 44 + 16;
  const vw    = window.innerWidth;
  const vh    = window.innerHeight;
  const left  = x + menuW > vw ? x - menuW : x;
  const top   = y + menuH > vh ? y - menuH : y;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => {
        setCopied(false);
        onClose();
      }, 1200);
    } catch {
      onClose();
    }
  };

  const handleAction = (id: string) => {
    if (id === 'copy')  { handleCopy();  return; }
    if (id === 'reply') { onReply(); onClose(); return; }
    if (id === 'edit')  { onEdit();  onClose(); return; }
    if (id === 'pin')   { onPin();   onClose(); return; }
    // delete — wired later
    onClose();
  };

  const visibleItems = BASE_ITEMS.filter(i => !i.ownOnly || isOwn);

  return createPortal(
    <div
      ref={menuRef}
      className="ctx-menu"
      style={{ top, left }}
      role="menu"
    >
      {visibleItems.map((item, i) => {
        const isCopyItem = item.id === 'copy';
        const showTick   = isCopyItem && copied;
        const Icon       = item.icon;

        return (
          <button
            key={item.id}
            className={`ctx-item ${item.danger ? 'danger' : ''} ${showTick ? 'copied' : ''}`}
            style={{ '--delay': `${i * 40}ms` } as React.CSSProperties}
            onClick={() => handleAction(item.id)}
            role="menuitem"
          >
            <span className="ctx-icon">
              {showTick
                ? <Check size={15} className="ctx-tick" />
                : <Icon  size={15} />}
            </span>
            <span>{showTick ? 'Copied!' : (item.id === 'pin' && isPinned) ? 'Unpin' : item.label}</span>
          </button>
        );
      })}
    </div>,
    document.body
  );
}
