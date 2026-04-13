import { useState, useEffect } from 'react';

export default function SlaTimerBadge({ ticket }) {
  const [currentDate, setCurrentDate] = useState(Date.now());
  const [fetchTime] = useState(Date.now()); // Approximation of receipt time

  const slaInfo = ticket.slaInfo;

  useEffect(() => {
    if (!slaInfo || slaInfo.deadlineTimestamp === -1) return;
    const timer = setInterval(() => setCurrentDate(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [slaInfo]);

  if (!slaInfo) return <span className="badge" style={{backgroundColor: 'transparent', color: 'var(--color-text-secondary)'}}>—</span>;

  // Paused / Completed
  if (slaInfo.deadlineTimestamp === -1) {
    if (slaInfo.remainingMs <= 0 && ticket.slaBreached) {
      return <span className="badge badge-sla-breach">⚠️ Süresi Doldu</span>;
    }
    if (slaInfo.remainingMs > 0) {
      const diff = slaInfo.remainingMs;
      const mins = Math.floor(diff / 60000);
      const secs = Math.floor((diff % 60000) / 1000);
      return (
        <span className="badge" style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text-secondary)' }}>
          {mins}dk {secs}sn (D)
        </span>
      );
    }
    return <span className="badge badge-neutral">Tamamlandı</span>;
  }

  // Active
  const elapsedSinceFetch = currentDate - fetchTime;
  const diff = slaInfo.remainingMs - elapsedSinceFetch;

  if (diff <= 0) {
    return <span className="badge badge-sla-breach">⚠️ Süresi Doldu</span>;
  }

  const mins = Math.floor(diff / 60000);
  const secs = Math.floor((diff % 60000) / 1000);

  let badgeClass = 'badge-success';
  if (mins < 1) badgeClass = 'badge-danger';
  else if (mins < 2) badgeClass = 'badge-warning';

  return <span className={`badge ${badgeClass}`}>{mins}dk {secs}sn</span>;
}
