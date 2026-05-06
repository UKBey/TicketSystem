export const CHART_COLORS = {
  created: 'var(--color-primary-500, #3b82f6)',
  resolved: 'var(--color-accent-500, #22c55e)',
  closed: 'var(--text-tertiary, #64748b)',
  slaBreach: 'var(--color-danger-500, #ef4444)',
  grid: 'var(--border-color-light, #e2e8f0)',
  axis: 'var(--text-tertiary, #94a3b8)',
};

export const PRODUCT_COLORS = [
  { bar: '#3b82f6', bg: 'rgba(59,130,246,0.12)', text: '#1d4ed8' },
  { bar: '#8b5cf6', bg: 'rgba(139,92,246,0.12)', text: '#6d28d9' },
  { bar: '#06b6d4', bg: 'rgba(6,182,212,0.12)',  text: '#0e7490' },
  { bar: '#f59e0b', bg: 'rgba(245,158,11,0.12)', text: '#b45309' },
  { bar: '#ec4899', bg: 'rgba(236,72,153,0.12)', text: '#be185d' },
  { bar: '#10b981', bg: 'rgba(16,185,129,0.12)', text: '#047857' },
  { bar: '#94a3b8', bg: 'rgba(148,163,184,0.10)', text: '#475569' },
];

export const SLA_TONE_COLORS = {
  goodBg: 'rgba(59, 130, 246, 0.10)',
  goodText: 'var(--color-primary-700, #1d4ed8)',
  warningBg: 'rgba(234, 179, 8, 0.14)',
  warningText: 'var(--color-warning-700, #a16207)',
  dangerBg: 'rgba(239, 68, 68, 0.12)',
  dangerText: 'var(--color-danger-700, #b91c1c)',
};

export const TIMELINE_SERIES = [
  { key: 'created', label: 'Created', color: CHART_COLORS.created },
  { key: 'resolved', label: 'Resolved', color: CHART_COLORS.resolved },
  { key: 'closed', label: 'Closed', color: CHART_COLORS.closed },
  { key: 'slaBreach', label: 'SLA Breach', color: CHART_COLORS.slaBreach },
];

export const COMPLETION_COLORS = {
  good:    '#22c55e',
  warning: '#f59e0b',
  danger:  '#ef4444',
  missing: 'rgba(239,68,68,0.18)',
};

export function getCompletionColor(rate) {
  if (rate >= 90) return COMPLETION_COLORS.good;
  if (rate >= 80) return COMPLETION_COLORS.warning;
  return COMPLETION_COLORS.danger;
}
