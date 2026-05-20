import { STATUS_COLORS, PRIORITY_COLORS } from '../theme/theme';

/** ISO tarihi "dd.MM.yyyy HH:mm" biçimine çevirir. */
export function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Bilet durumunun rengini döner. */
export function statusColor(status) {
  return STATUS_COLORS[status] || '#64748b';
}

/** Öncelik rengini döner. */
export function priorityColor(priority) {
  return PRIORITY_COLORS[priority] || '#64748b';
}

/** Durum kodunu okunabilir etikete çevirir (i18n yoksa kod gösterilir). */
export function statusLabel(status, t) {
  return t(`status.${status}`, status || '—');
}

/** Öncelik kodunu okunabilir etikete çevirir. */
export function priorityLabel(priority, t) {
  return t(`priority.${priority}`, priority || '—');
}
