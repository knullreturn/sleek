import React from 'react';

export function TypingIndicator({ names }: { names: string[] }) {
  if (names.length === 0) return <div className="typing-indicator" />;

  const label =
    names.length === 1
      ? `${names[0]} is typing`
      : names.length === 2
      ? `${names[0]} and ${names[1]} are typing`
      : 'Several people are typing';

  return (
    <div className="typing-indicator animate-fade-in">
      <div className="typing-dots" aria-hidden>
        <span className="typing-dot" />
        <span className="typing-dot" />
        <span className="typing-dot" />
      </div>
      <span>{label}</span>
    </div>
  );
}
