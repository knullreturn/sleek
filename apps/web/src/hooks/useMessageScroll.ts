import { useEffect, useRef, useState } from 'react';

interface UseMessageScrollOptions {
  chatId:       string;
  messages:     any[];
  myId?:        string;
  typingNames:  string[];
}

interface UseMessageScrollResult {
  canvasRef:        React.RefObject<HTMLDivElement>;
  bottomRef:        React.RefObject<HTMLDivElement>;
  isScrolledUp:     boolean;
  newMsgCount:      number;
  scrollToBottom:   () => void;
}

/**
 * Owns ALL scroll behaviour for ChatWindow:
 *  - instant jump on first load (so fly-in animation plays at rest)
 *  - auto-scroll on own messages / when already at bottom
 *  - unread counter when scrolled up and peer sends a message
 *  - keeps view pinned when typing indicator appears
 *  - suppresses FAB during programmatic scrolls
 *  - debounces FAB visibility to prevent flash from message fly-in animations
 */
export function useMessageScroll({
  chatId,
  messages,
  myId,
  typingNames,
}: UseMessageScrollOptions): UseMessageScrollResult {
  const canvasRef            = useRef<HTMLDivElement>(null);
  const bottomRef            = useRef<HTMLDivElement>(null);
  const initialScrollDone    = useRef(false);
  const isScrolledUpRef      = useRef(false);
  const isProgrammaticScroll = useRef(false);
  // Debounce timer — FAB only shows after user is scrolled up for 200ms continuously
  const fabDebounceTimer     = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [isScrolledUp, setIsScrolledUp] = useState(false);
  const [newMsgCount,  setNewMsgCount]  = useState(0);

  // Reset on chat switch
  useEffect(() => {
    setIsScrolledUp(false);
    setNewMsgCount(0);
    isScrolledUpRef.current   = false;
    initialScrollDone.current = false;
    if (fabDebounceTimer.current) clearTimeout(fabDebounceTimer.current);
  }, [chatId]);

  // Track user scroll position — debounce showing the FAB to avoid animation flashes
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const onScroll = () => {
      if (isProgrammaticScroll.current) return;

      const distFromBottom = canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight;
      const scrolledUp     = distFromBottom > 120;

      isScrolledUpRef.current = scrolledUp;

      if (!scrolledUp) {
        // Hide FAB instantly — no delay when scrolling back to bottom
        if (fabDebounceTimer.current) clearTimeout(fabDebounceTimer.current);
        setIsScrolledUp(false);
        setNewMsgCount(0);
      } else {
        // Show FAB only after 200ms of being continuously scrolled up
        // This swallows the brief blip caused by message fly-in animations
        if (!isScrolledUp) {
          if (fabDebounceTimer.current) clearTimeout(fabDebounceTimer.current);
          fabDebounceTimer.current = setTimeout(() => {
            if (isScrolledUpRef.current) setIsScrolledUp(true);
          }, 200);
        }
      }
    };

    canvas.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      canvas.removeEventListener('scroll', onScroll);
      if (fabDebounceTimer.current) clearTimeout(fabDebounceTimer.current);
    };
  }, [chatId, isScrolledUp]);

  // Initial load jump + auto-scroll on new messages
  useEffect(() => {
    if (messages.length === 0) return;

    if (!initialScrollDone.current) {
      isProgrammaticScroll.current = true;
      bottomRef.current?.scrollIntoView({ behavior: 'instant' });
      setTimeout(() => { isProgrammaticScroll.current = false; }, 150);
      initialScrollDone.current = true;
      return;
    }

    const lastMsg      = messages[messages.length - 1];
    const isOwnMessage = lastMsg?.senderId === myId;

    if (isScrolledUpRef.current && !isOwnMessage) {
      setNewMsgCount((c) => c + 1);
    } else {
      // Set programmatic flag BEFORE the scroll so the event handler ignores it
      isProgrammaticScroll.current = true;
      bottomRef.current?.scrollIntoView({ behavior: 'instant' });
      setTimeout(() => { isProgrammaticScroll.current = false; }, 150);
    }
  }, [messages, myId]);

  // Keep bottom in view when typing indicator appears
  useEffect(() => {
    if (typingNames.length === 0) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (canvas.scrollHeight - canvas.scrollTop - canvas.clientHeight < 120) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [typingNames]);

  const scrollToBottom = () => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    setNewMsgCount(0);
    setIsScrolledUp(false);
  };

  return { canvasRef, bottomRef, isScrolledUp, newMsgCount, scrollToBottom };
}
