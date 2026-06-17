import { useEffect, useRef, useState } from 'react';

/**
 * Cocugunu, scroll ile gorunume girince BIR KEZ fade + yukari kayma ile
 * canlandirir (IntersectionObserver). IntersectionObserver yoksa (eski
 * tarayici / jsdom test ortami) ya da kullanici hareket azaltma istiyorsa
 * icerik aninda gorunur — hicbir sey gizli kalmaz.
 *
 * `delay` ile pespese kartlara kademeli (stagger) giris verilebilir.
 */
export default function Reveal({ children, delay = 0, className = '', style, as: Tag = 'div', ...rest }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(() => {
    if (typeof IntersectionObserver === 'undefined') return true;
    if (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return true;
    }
    return false;
  });

  useEffect(() => {
    if (visible) return undefined;
    const el = ref.current;
    if (!el) return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -32px 0px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [visible]);

  const mergedStyle =
    delay && visible ? { ...style, animationDelay: `${delay}ms` } : style;

  return (
    <Tag
      ref={ref}
      className={`${visible ? 'reveal-in' : 'reveal-pending'}${className ? ` ${className}` : ''}`}
      style={mergedStyle}
      {...rest}
    >
      {children}
    </Tag>
  );
}
