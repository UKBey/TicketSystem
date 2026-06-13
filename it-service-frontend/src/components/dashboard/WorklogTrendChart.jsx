import { memo, useMemo } from 'react';
import { Clock3 } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { CHART_COLORS } from './ChartColors';
import './dashboard.css';

const dateLabel = new Intl.DateTimeFormat('en-US', { day: 'numeric', month: 'short' });

function normalize(data) {
  const rows = Array.isArray(data) ? data : [];
  return rows
    .map((row) => {
      const d = row?.date ? new Date(`${row.date}T00:00:00Z`) : null;
      return {
        dateKey: row?.date ?? '',
        dateLabel: d ? dateLabel.format(d) : '-',
        hours: Math.round((Number(row?.minutes ?? 0) / 60) * 10) / 10,
      };
    })
    .sort((a, b) => a.dateKey.localeCompare(b.dateKey));
}

function TooltipBox({ active, payload, label, hoursLabel }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border px-3 py-2 text-xs shadow-lg" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <p className="mb-1 text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{label}</p>
      <div className="flex items-center justify-between gap-4">
        <span style={{ color: 'var(--text-secondary)' }}>{hoursLabel}</span>
        <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>{payload[0].value}h</span>
      </div>
    </div>
  );
}

function WorklogTrendChart({
  data,
  loading,
  badgeLabel = 'Worklog',
  title = 'Daily logged hours',
  subtitle = '',
  emptyText = 'No worklog logged in this period.',
  hoursLabel = 'Hours',
}) {
  const chartData = useMemo(() => normalize(data), [data]);
  const hasData = chartData.some((d) => d.hours > 0);

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5">
        <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          <Clock3 className="h-3.5 w-3.5" />
          {badgeLabel}
        </div>
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h2>
        {subtitle && <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>{subtitle}</p>}
      </div>

      {loading ? (
        <div className="timeline-chart-inner animate-pulse rounded-2xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
      ) : !hasData ? (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          {emptyText}
        </div>
      ) : (
        <div className="timeline-chart-scroll">
          <div className="timeline-chart-inner">
            {/* initialDimension: ilk frame'de Recharts default -1x-1 olcumuyle uyari basmasin
                diye container'in gercek boyutunu (.timeline-chart-inner: 680x340) veriyoruz. */}
            <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: 680, height: 340 }}>
              <BarChart data={chartData} margin={{ top: 10, right: 8, left: -8, bottom: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
                <XAxis dataKey="dateLabel" tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} interval="preserveStartEnd" minTickGap={16} />
                <YAxis allowDecimals tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} domain={[0, 'auto']} />
                <Tooltip cursor={{ fill: 'var(--bg-surface-secondary)' }} content={<TooltipBox hoursLabel={hoursLabel} />} />
                <Bar dataKey="hours" fill={CHART_COLORS.created} radius={[4, 4, 0, 0]} maxBarSize={28} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </section>
  );
}

export default memo(WorklogTrendChart);
