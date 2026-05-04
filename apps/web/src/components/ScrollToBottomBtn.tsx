import { ArrowDown } from 'lucide-react';

interface Props {
  count:   number;
  onClick: () => void;
}

// Floating scroll-to-bottom button shown when the user is scrolled up
// and new messages have arrived. Animates in/out via CSS class.
export function ScrollToBottomBtn({ count, onClick }: Props) {
  return (
    <button
      className="scroll-to-bottom-btn"
      onClick={onClick}
      aria-label={count > 0 ? `${count} new messages — scroll to bottom` : 'Scroll to bottom'}
      title="Scroll to bottom"
    >
      <ArrowDown size={16} />
      {count > 0 && (
        <span className="scroll-btn-count">{count > 99 ? '99+' : count}</span>
      )}
    </button>
  );
}
