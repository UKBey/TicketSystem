import { useTheme } from '../context/ThemeContext';

const STATUS_MAP = {
  NEW: { label: 'New', light: { bg: '#dbeafe', color: '#1e40af' }, dark: { bg: 'rgba(59,130,246,0.2)', color: '#93c5fd' } },
  IN_PROGRESS: { label: 'In Progress', light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' } },
  WAITING_FOR_CUSTOMER: { label: 'Waiting', light: { bg: '#ede9fe', color: '#5b21b6' }, dark: { bg: 'rgba(139,92,246,0.2)', color: '#c4b5fd' } },
  RESOLVED: { label: 'Resolved', light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)', color: '#86efac' } },
  CLOSED: { label: 'Closed', light: { bg: '#f1f5f9', color: '#475569' }, dark: { bg: 'rgba(100,116,139,0.2)', color: '#cbd5e1' } },
};

export function StatusBadge({ status }) {
  const { theme } = useTheme();
  const info = STATUS_MAP[status] || { label: status, light: { bg: '#f1f5f9', color: '#475569' }, dark: { bg: 'rgba(100,116,139,0.2)', color: '#cbd5e1' } };
  const colors = theme === 'dark' ? info.dark : info.light;
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap"
      style={{ backgroundColor: colors.bg, color: colors.color }}
    >
      {info.label}
    </span>
  );
}

const PRIORITY_MAP = {
  LOW: { label: 'Low', light: { bg: '#dcfce7', color: '#166534' }, dark: { bg: 'rgba(34,197,94,0.2)', color: '#86efac' } },
  MEDIUM: { label: 'Medium', light: { bg: '#fef3c7', color: '#92400e' }, dark: { bg: 'rgba(245,158,11,0.2)', color: '#fde68a' } },
  HIGH: { label: 'High', light: { bg: '#fee2e2', color: '#991b1b' }, dark: { bg: 'rgba(239,68,68,0.2)', color: '#fca5a5' } },
  CRITICAL: { label: 'Critical', light: { bg: '#fecaca', color: '#7f1d1d' }, dark: { bg: 'rgba(239,68,68,0.3)', color: '#fca5a5' } },
};

export function PriorityBadge({ priority }) {
  const { theme } = useTheme();
  const info = PRIORITY_MAP[priority] || { label: priority, light: { bg: '#f1f5f9', color: '#475569' }, dark: { bg: 'rgba(100,116,139,0.2)', color: '#cbd5e1' } };
  const colors = theme === 'dark' ? info.dark : info.light;
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap"
      style={{ backgroundColor: colors.bg, color: colors.color }}
    >
      {info.label}
    </span>
  );
}
