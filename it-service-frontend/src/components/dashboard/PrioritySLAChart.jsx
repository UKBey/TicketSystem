import { memo, useMemo } from 'react';
import { BarChart3 } from 'lucide-react';
import { SLA_TONE_COLORS } from './ChartColors';
import PrioritySLARow from './PrioritySLARow';
import './dashboard.css';

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
}

function normalizePriorityMetrics(data) {
  const rows = Array.isArray(data?.priorityMetrics) ? data.priorityMetrics : [];

  const order = {
    CRITICAL: 1,
    HIGH: 2,
    MEDIUM: 3,
    LOW: 4,
  };

  return [...rows]
    .map((row) => ({
      priority: row?.priority ?? '-',
      ticketCount: Number(row?.ticketCount ?? 0),
      slaTargetHours: Number(row?.slaTargetHours ?? 0),
      avgResolutionHours: Number(row?.avgResolutionHours ?? 0),
      breachCount: Number(row?.breachCount ?? 0),
      breachPercentage: Number(row?.breachPercentage ?? 0),
      onTimePercentage: Number(row?.onTimePercentage ?? 0),
    }))
    .sort((left, right) => (order[left.priority] ?? 99) - (order[right.priority] ?? 99));
}

function PrioritySLAChart({ data, loading }) {
  const items = useMemo(() => normalizePriorityMetrics(data), [data]);

  const maxScaleHours = Math.max(48, ...items.map((item) => item.slaTargetHours || 0), ...items.map((item) => item.avgResolutionHours || 0));

  const summary = items.reduce(
    (accumulator, item) => {
      accumulator.ticketCount += item.ticketCount;
      accumulator.breachCount += item.breachCount;
      accumulator.onTimeAverage += item.onTimePercentage;
      return accumulator;
    },
    { ticketCount: 0, breachCount: 0, onTimeAverage: 0 }
  );

  const averageOnTime = items.length > 0 ? summary.onTimeAverage / items.length : 0;

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5 flex flex-col items-start justify-between gap-3 sm:flex-row sm:gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <BarChart3 className="h-3.5 w-3.5" />
            Priority SLA Comparison
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>SLA target vs. avg. resolution time</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            SLA target and average resolution time compared on the same scale.
          </p>
        </div>

        <div className="rounded-2xl px-3 py-2 text-right" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
          <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Avg on-time</div>
          <div className="text-xl font-black" style={{ color: 'var(--text-primary)' }}>{averageOnTime.toFixed(0)}%</div>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2 text-xs font-medium">
        <span className="priority-sla-chip" style={{ backgroundColor: SLA_TONE_COLORS.goodBg, color: SLA_TONE_COLORS.goodText }}>
          90%+ on target
        </span>
        <span className="priority-sla-chip" style={{ backgroundColor: SLA_TONE_COLORS.warningBg, color: SLA_TONE_COLORS.warningText }}>
          80–90% warning
        </span>
        <span className="priority-sla-chip" style={{ backgroundColor: SLA_TONE_COLORS.dangerBg, color: SLA_TONE_COLORS.dangerText }}>
          &lt; 80% at risk
        </span>
        <span className="priority-sla-chip" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
          Max scale: {formatNumber(maxScaleHours)}h
        </span>
      </div>

      {!loading && items.length === 0 ? (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          No priority SLA metrics found.
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <PrioritySLARow
              key={item.priority}
              item={item}
              maxScaleHours={maxScaleHours}
            />
          ))}
        </div>
      )}
    </section>
  );
}

export default memo(PrioritySLAChart);
