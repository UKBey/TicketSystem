export const STATUS_OPTIONS = {
  NEW: ['IN_PROGRESS'],
  IN_PROGRESS: ['NEW', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'],
  WAITING_FOR_CUSTOMER: ['IN_PROGRESS'],
  RESOLVED: ['IN_PROGRESS', 'CLOSED'],
  CLOSED: [],
};

export function formatSlaTime(ms) {
  const totalSecs = Math.floor(ms / 1000);
  const totalMins = Math.floor(totalSecs / 60);
  if (totalMins < 60) {
    const secs = totalSecs % 60;
    return `${totalMins}m ${secs}s`;
  }
  const hours = Math.floor(totalMins / 60);
  const mins = totalMins % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

export function formatDate(dateStr) {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString('en-US', {
    month: 'numeric', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true,
  });
}

export function formatShortDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: true,
  });
}

export function formatMinutes(mins) {
  if (!mins) return '0m';
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

export function getAuditActionStyles(actionType, isDark) {
  if (actionType === 'CLOSE') {
    return {
      backgroundColor: isDark ? 'rgba(239,68,68,0.18)' : '#fee2e2',
      color: isDark ? '#fca5a5' : '#991b1b',
    };
  }
  if (actionType === 'UNCLAIM') {
    return {
      backgroundColor: isDark ? 'rgba(245,158,11,0.18)' : '#fef3c7',
      color: isDark ? '#fde68a' : '#92400e',
    };
  }
  return {
    backgroundColor: isDark ? 'rgba(59,130,246,0.18)' : '#dbeafe',
    color: isDark ? '#93c5fd' : '#1d4ed8',
  };
}
