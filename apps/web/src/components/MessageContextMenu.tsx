import React, { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Copy, Reply, Pencil, Pin, Trash2 } from 'lucide-react';

interface MessageContextMenuProps {
  x:       number;
  y:       number;
  isOwn:   boolean;
  onClose: () => void;
}

const ITEMS = [
  { id: 'copy',   label: 'Copy',   icon: Copy,   ownOnly: false, danger: false },
  { id: 'reply',  label: 'Reply',  icon: Reply,  ownOnly: false, danger: false },
  { id: 'edit',   label: 'Edit',   icon: Pencil, ownOnly: true,  danger: false },
  { id: 'pin',    label: 'Pin',    icon: Pin,    ownOnly: false, danger: false },
  { id: 'delete', label: 'Delete', icon: Trash2, ownOnly: true,  danger: true  },
];

export function MessageContextMenu({ x, y, isOwn, onClose }: MessageContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);

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

  // Smart positioning — keep on screen
  const menuW = 200;
  const menuH = ITEMS.filter(i => !i.ownOnly || isOwn).length * 44 + 16;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const left = x + menuW > vw ? x - menuW : x;
  const top  = y + menuH > vh ? y - menuH : y;

  const visibleItems = ITEMS.filter(i => !i.ownOnly || isOwn);

  return createPortal(
    <div
      ref={menuRef}
      className="ctx-menu"
      style={{ top, left }}
      role="menu"
    >
      {visibleItems.map((item, i) => {
        const Icon = item.icon;
        return (
          <button
            key={item.id}
            className={`ctx-item ${item.danger ? 'danger' : ''}`}
            style={{ '--delay': `${i * 40}ms` } as React.CSSProperties}
            onClick={onClose}
            role="menuitem"
          >
            <span className="ctx-icon"><Icon size={15} /></span>
            <span>{item.label}</span>
          </button>
        );
      })}
    </div>,
    document.body
  );
}
