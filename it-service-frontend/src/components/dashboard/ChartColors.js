export const CHART_COLORS = {
  created: 'var(--color-primary-500, #3b82f6)',
  resolved: 'var(--color-accent-500, #22c55e)',
  closed: 'var(--text-tertiary, #64748b)',
  slaBreach: 'var(--color-danger-500, #ef4444)',
  grid: 'var(--border-color-light, #e2e8f0)',
  axis: 'var(--text-tertiary, #94a3b8)',
};

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
