import { useTranslation } from 'react-i18next';
import { useTheme } from '../context/ThemeContext';
import { STATUS_COLORS, PRIORITY_COLORS } from '../constants/ticketColors';

const STATUS_KEYS = {
  NEW: 'ticket.status.new',
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
