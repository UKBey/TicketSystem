import { formatDate as fmtDate, formatDateTime as fmtDateTime } from './dateFormat';

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

// Tarih biçimlendirme tek kaynaktan (utils/dateFormat) — kullanıcının seçtiği formatı
// kullanır. formatDate = tarih + saat; formatShortDate = yalnız tarih (geriye dönük
// uyumluluk için boş değerde '' döner).
export function formatDate(dateStr) {
  return fmtDateTime(dateStr);
}

export function formatShortDate(dateStr) {
  return dateStr ? fmtDate(dateStr) : '';
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
