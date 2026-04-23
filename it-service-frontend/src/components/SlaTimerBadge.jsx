import { useRef } from 'react';
import { useTheme } from '../context/ThemeContext';

export default function SlaTimerBadge({ ticket, now }) {
  const fetchTimeRef = useRef(Date.now());
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const slaInfo = ticket.slaInfo;

  if (!slaInfo) return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>—</span>;

  const badgeStyle = (type) => {
    const styles = {
      breach: { backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' },
      warning: { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' },
      success: { backgroundColor: isDark ? 'rgba(34,197,94,0.2)' : '#dcfce7', color: isDark ? '#86efac' : '#166534' },
      neutral: { backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' },
    };
    return styles[type];
  };

  const baseCls = 'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold';

  // Sayaç duraklatilmis veya surec tamamlanmis senaryosu.
  if (slaInfo.deadlineTimestamp === -1) {
    if (slaInfo.remainingMs <= 0 && ticket.slaBreached) {
      return <span className={`${baseCls} font-bold animate-pulse-subtle`} style={badgeStyle('breach')}>Expired</span>;
    }
    if (slaInfo.remainingMs > 0) {
      const diff = slaInfo.remainingMs;
      const mins = Math.floor(diff / 60000);
      const secs = Math.floor((diff % 60000) / 1000);
      return <span className={baseCls} style={badgeStyle('neutral')}>{mins}m {secs}s (P)</span>;
    }
    return <span className={baseCls} style={badgeStyle('neutral')}>Completed</span>;
  }

  // Aktif sayaçta kalan sureyi istemci tarafinda saniye saniye gunceller.
  const elapsedSinceFetch = now - fetchTimeRef.current;
  const diff = slaInfo.remainingMs - elapsedSinceFetch;

  if (diff <= 0) {
    return <span className={`${baseCls} font-bold animate-pulse-subtle`} style={badgeStyle('breach')}>Expired</span>;
  }

  const mins = Math.floor(diff / 60000);
  const secs = Math.floor((diff % 60000) / 1000);

  let type = 'success';
  let extraCls = '';
  if (mins < 1) { type = 'breach'; extraCls = 'animate-pulse-subtle font-bold'; }
  else if (mins < 2) { type = 'warning'; }

  return <span className={`${baseCls} ${extraCls}`} style={badgeStyle(type)}>{mins}m {secs}s</span>;
}
