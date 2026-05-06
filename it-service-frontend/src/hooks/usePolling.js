import { useEffect, useRef } from 'react';

export function usePolling(callback, intervalMs, immediate = false) {
  const savedCallback = useRef(callback);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (immediate) savedCallback.current();
    if (!intervalMs) return;
    const id = setInterval(() => savedCallback.current(), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs, immediate]);
}
