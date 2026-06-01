import { useCallback, useEffect, useRef } from 'react';

/**
 * Anchors a popover to a trigger element using `position: fixed`, set imperatively on the
 * popover node. Fixed positioning is used (instead of `absolute`) so the popover is never
 * clipped by an ancestor's {@code overflow: hidden} (the comment composer card uses it).
 *
 * Returns a callback ref to attach to the popover's root element. The position is computed
 * on mount and kept in sync on window scroll/resize while open — without any React state, so
 * it never triggers re-renders.
 *
 * @param {React.RefObject<HTMLElement>} triggerRef element to anchor to
 * @param {boolean} open whether the popover is currently mounted/shown
 * @param {{ gap?: number, align?: 'left'|'right', placement?: 'top'|'bottom' }} opts
 * @returns {(node: HTMLElement|null) => void} ref callback for the popover root
 */
export function useAnchoredPosition(triggerRef, open, { gap = 8, align = 'right', placement = 'top' } = {}) {
  const nodeRef = useRef(null);

  const position = useCallback(() => {
    const node = nodeRef.current;
    const trigger = triggerRef.current;
    if (!node || !trigger) return;
    const r = trigger.getBoundingClientRect();
    node.style.position = 'fixed';
    if (placement === 'top') {
      node.style.bottom = `${Math.round(window.innerHeight - r.top + gap)}px`;
      node.style.top = 'auto';
    } else {
      node.style.top = `${Math.round(r.bottom + gap)}px`;
      node.style.bottom = 'auto';
    }
    if (align === 'right') {
      node.style.right = `${Math.round(window.innerWidth - r.right)}px`;
      node.style.left = 'auto';
    } else {
      node.style.left = `${Math.round(r.left)}px`;
      node.style.right = 'auto';
    }
  }, [triggerRef, gap, align, placement]);

  const setRef = useCallback((node) => {
    nodeRef.current = node;
    if (node) position();
  }, [position]);

  useEffect(() => {
    if (!open) return undefined;
    position();
    window.addEventListener('resize', position);
    window.addEventListener('scroll', position, true);
    return () => {
      window.removeEventListener('resize', position);
      window.removeEventListener('scroll', position, true);
    };
  }, [open, position]);

  return setRef;
}
