import React, { useRef } from 'react';
import { Send, Paperclip, Smile } from 'lucide-react';

interface ChatInputProps {
  input:          string;
  onInputChange:  (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  onKeyDown:      (e: React.KeyboardEvent<HTMLTextAreaElement>) => void;
  onSend:         () => void;
  textareaRef:    React.RefObject<HTMLTextAreaElement>;
}

// ── ChatInput ─────────────────────────────────────────────────────────────────
// The message compose bar at the bottom of the chat window.
export function ChatInput({ input, onInputChange, onKeyDown, onSend, textareaRef }: ChatInputProps) {
  const comingSoon = (feature: string) =>
    alert(`${feature} is coming soon!`); // replace with toast when toast system is added

  return (
    <div className="chat-input-bar">
      <button className="icon-btn" title="Attach file (coming soon)" aria-label="Attach file"
        onClick={() => comingSoon('File attachments')}>
        <Paperclip size={18} />
      </button>

      <textarea
        ref={textareaRef}
        id="message-input"
        className="input-field"
        placeholder="Message…"
        value={input}
        onChange={onInputChange}
        onKeyDown={onKeyDown}
        rows={1}
        style={{ height: 40 }}
        aria-label="Message input"
      />

      <button className="icon-btn" title="Emoji (coming soon)" aria-label="Emoji"
        onClick={() => comingSoon('Emoji picker')}>
        <Smile size={18} />
      </button>

      <button
        id="send-btn"
        className="send-btn"
        onClick={onSend}
        disabled={!input.trim()}
        title="Send"
        aria-label="Send message"
      >
        <Send size={16} />
      </button>
    </div>
  );
}
