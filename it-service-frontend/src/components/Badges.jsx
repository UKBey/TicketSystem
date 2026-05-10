import { useTranslation } from 'react-i18next';
import { useTheme } from '../context/ThemeContext';

const STATUS_COLORS = {
  NEW:                  { light: { bg: '#dbeafe', color: '#1e40af' }, dark: { bg: 'rgba(59,130,246,0.2)', color: '#93c5fd' } },
  IN_PROGRESS:          { light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' } },
  WAITING_FOR_CUSTOMER: { light: { bg: '#ede9fe', color: '#5b21b6' }, dark: { bg: 'rgba(139,92,246,0.2)', color: '#c4b5fd' } },
  RESOLVED:             { light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)', color: '#86efac' } },
  CLOSED:               { light: { bg: '#f1f5f9', color: '#475569' }, dark: { bg: 'rgba(100,116,139,0.2)', color: '#cbd5e1' } },
};

const STATUS_KEYS = {
  NEW: 'ticket.status.open',
  IN_PROGRESS: 'ticket.status.in_progress',
  WAITING_FOR_CUSTOMER: 'ticket.status.waiting_for_customer',
  RESOLVED: 'ticket.status.resolved',
  CLOSED: 'ticket.status.closed',
};

export function StatusBadge({ status }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const colors = (STATUS_COLORS[status] ?? STATUS_COLORS.CLOSED)[theme === 'dark' ? 'dark' : 'light'];
  const label = STATUS_KEYS[status] ? t(STATUS_KEYS[status]) : status;
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap"
      style={{ backgroundColor: colors.bg, color: colors.color }}
    >
      {label}
    </span>
  );
}

const PRIORITY_COLORS = {
  LOW:      { light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)', color: '#86efac' } },
  MEDIUM:   { light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' } },
  HIGH:     { light: { bg: '#fee2e2', color: '#991b1b' }, dark: { bg: 'rgba(239,68,68,0.2)', color: '#fca5a5' } },
  CRITICAL: { light: { bg: '#fecaca', color: '#7f1d1d' }, dark: { bg: 'rgba(239,68,68,0.3)', color: '#fca5a5' } },
};

const PRIORITY_KEYS = {
  LOW: 'ticket.priority.low',
  MEDIUM: 'ticket.priority.medium',
  HIGH: 'ticket.priority.high',
  CRITICAL: 'ticket.priority.critical',
};

export function PriorityBadge({ priority }) {
  const { theme } = useTheme();
  const { t } = useTranslation();
  const colors = (PRIORITY_COLORS[priority] ?? PRIORITY_COLORS.LOW)[theme === 'dark' ? 'dark' : 'light'];
  const label = PRIORITY_KEYS[priority] ? t(PRIORITY_KEYS[priority]) : priority;
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap"
      style={{ backgroundColor: colors.bg, color: colors.color }}
    >
      {label}
    </span>
  );
}
