export const CHART_COLORS = {
  created: 'var(--color-primary-500, #3b82f6)',
  resolved: 'var(--color-accent-500, #22c55e)',
  closed: 'var(--text-tertiary, #64748b)',
  slaBreach: 'var(--color-danger-500, #ef4444)',
  grid: 'var(--border-color-light, #e2e8f0)',
  axis: 'var(--text-tertiary, #94a3b8)',
};

export const TIMELINE_SERIES = [
  { key: 'created', label: 'Created', color: CHART_COLORS.created },
  { key: 'resolved', label: 'Resolved', color: CHART_COLORS.resolved },
  { key: 'closed', label: 'Closed', color: CHART_COLORS.closed },
  { key: 'slaBreach', label: 'SLA Breach', color: CHART_COLORS.slaBreach },
];
