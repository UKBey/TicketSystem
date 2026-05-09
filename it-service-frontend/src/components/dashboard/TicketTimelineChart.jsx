import { memo, useMemo, useState } from 'react';
import { LineChart as LineChartIcon } from 'lucide-react';
import {
  Brush,
  CartesianGrid,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  LineChart,
} from 'recharts';
import { CHART_COLORS, TIMELINE_SERIES } from './ChartColors';
import './dashboard.css';

const dateLabel = new Intl.DateTimeFormat('en-US', {
  day: 'numeric',
  month: 'short',
});

function normalizeTimeline(data) {
  const rows = Array.isArray(data?.timeline) ? data.timeline : [];

  return rows
    .map((row) => {
      const date = row?.date ? new Date(`${row.date}T00:00:00Z`) : null;
      return {
        dateKey: row?.date ?? '',
        dateLabel: date ? dateLabel.format(date) : '-',
        created: Number(row?.created ?? 0),
        resolved: Number(row?.resolved ?? 0),
        closed: Number(row?.closed ?? 0),
        slaBreach: Number(row?.slaBreach ?? 0),
      };
    })
    .sort((left, right) => left.dateKey.localeCompare(right.dateKey));
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) {
    return null;
  }

  return (
    <div className="rounded-xl border px-3 py-2 text-xs shadow-lg" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <p className="mb-2 text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{label}</p>
      <div className="space-y-1">
        {payload.map((entry) => (
          <div key={entry.dataKey} className="flex items-center justify-between gap-4">
            <span className="inline-flex items-center gap-2" style={{ color: 'var(--text-secondary)' }}>
              <span className="h-2 w-2 rounded-full" style={{ backgroundColor: entry.color }} />
              {entry.name}
            </span>
            <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>{entry.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function TicketTimelineChart({ data, loading }) {
  const chartData = useMemo(() => normalizeTimeline(data), [data]);
  const [visibleSeries, setVisibleSeries] = useState(() => ({
    created: true,
    resolved: true,
    closed: true,
    slaBreach: true,
  }));

  const handleLegendClick = (series) => {
    if (!series?.dataKey) {
      return;
    }

    setVisibleSeries((prev) => ({
      ...prev,
      [series.dataKey]: !prev[series.dataKey],
    }));
  };

  const hasData = chartData.length > 0;

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <LineChartIcon className="h-3.5 w-3.5" />
            Trend Chart
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Created, Resolved, Closed & SLA Breach</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Toggle series from the legend. Use the bar below to adjust the date range.
          </p>
        </div>
      </div>

      {!loading && !hasData ? (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          No timeline data available.
        </div>
      ) : (
        <div className="timeline-chart-scroll">
          <div className="timeline-chart-inner">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 8, left: -8, bottom: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} />
                <XAxis dataKey="dateLabel" tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} />
                <YAxis allowDecimals={false} tick={{ fill: CHART_COLORS.axis, fontSize: 12 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} domain={[0, 'auto']} />
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  verticalAlign="top"
                  height={36}
                  iconType="circle"
                  wrapperStyle={{ fontSize: 12 }}
                  formatter={(value, entry) => {
                    const enabled = visibleSeries[entry.dataKey];
                    return <span className="timeline-legend-button" style={{ color: enabled ? 'var(--text-primary)' : 'var(--text-tertiary)' }}>{value}</span>;
                  }}
                  onClick={handleLegendClick}
                />

                {TIMELINE_SERIES.map((series) => (
                  <Line
                    key={series.key}
                    type="monotone"
                    dataKey={series.key}
                    name={series.label}
                    stroke={series.color}
                    strokeWidth={series.key === 'slaBreach' ? 2.5 : 3}
                    strokeDasharray={series.key === 'slaBreach' ? '6 4' : undefined}
                    dot={false}
                    activeDot={{ r: 5 }}
                    hide={!visibleSeries[series.key] || loading}
                  />
                ))}

                <Brush
                  dataKey="dateLabel"
                  height={32}
                  stroke="var(--border-color)"
                  fill="var(--bg-surface-secondary)"
                  travellerWidth={8}
                  tickFormatter={(v) => v}
                  style={{ fontSize: 11 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </section>
  );
}

export default memo(TicketTimelineChart);
