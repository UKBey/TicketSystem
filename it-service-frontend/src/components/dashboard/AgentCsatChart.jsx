import { memo, useMemo } from 'react';
import { Star } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { CHART_COLORS } from './ChartColors';

const RATING_COLOR = { 5: '#22c55e', 4: '#84cc16', 3: '#f59e0b', 2: '#f97316', 1: '#ef4444' };
const ORDER = [5, 4, 3, 2, 1];
const dateLabel = new Intl.DateTimeFormat('en-US', { day: 'numeric', month: 'short' });

function TrendTooltip({ active, payload, label }) {
  if (!active || !payload?.length || payload[0].value == null) return null;
  return (
    <div className="rounded-xl border px-3 py-2 text-xs shadow-lg" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <p className="mb-1 text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{label}</p>
      <div className="flex items-center gap-1.5">
        <Star className="h-3 w-3" fill="#f59e0b" style={{ color: '#f59e0b' }} />
        <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>{Number(payload[0].value).toFixed(2)}</span>
      </div>
    </div>
  );
}

function AgentCsatChart({
  data,
  loading,
  badgeLabel = 'Satisfaction',
  title = 'My CSAT survey results',
  subtitle = '',
  responsesLabel = '',
  emptyText = 'No CSAT responses in this period.',
  distributionLabel = 'Rating distribution',
  trendLabel = 'Average over time',
}) {
  const dist = data?.ratingDistribution ?? {};
  const total = Number(data?.totalResponses ?? 0);
  const average = Number(data?.average ?? 0);

  const trend = useMemo(() => {
    const rows = Array.isArray(data?.trend) ? data.trend : [];
    return rows.map((r) => {
      const d = r?.date ? new Date(`${r.date}T00:00:00Z`) : null;
      return { dateLabel: d ? dateLabel.format(d) : '-', avg: r?.avg != null ? Number(r.avg) : null };
    });
  }, [data]);
  const hasTrend = trend.some((p) => p.avg != null);
  const maxCount = Math.max(1, ...ORDER.map((r) => Number(dist[r] ?? 0)));

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5">
        <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          <Star className="h-3.5 w-3.5" />
          {badgeLabel}
        </div>
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h2>
        {subtitle && <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>{subtitle}</p>}
      </div>

      {loading ? (
        <div className="h-56 animate-pulse rounded-2xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
      ) : total === 0 ? (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          {emptyText}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Headline + distribution */}
          <div>
            <div className="mb-4 flex items-end gap-2">
              <span className="text-4xl font-black leading-none" style={{ color: 'var(--text-primary)' }}>{average.toFixed(1)}</span>
              <span className="mb-1 text-sm" style={{ color: 'var(--text-tertiary)' }}>/5</span>
              <span className="mb-1 ml-auto text-xs" style={{ color: 'var(--text-tertiary)' }}>{responsesLabel}</span>
            </div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{distributionLabel}</p>
            <div className="space-y-1.5">
              {ORDER.map((r) => {
                const count = Number(dist[r] ?? 0);
                const pct = total > 0 ? Math.round((count / total) * 100) : 0;
                const w = (count / maxCount) * 100;
                return (
                  <div key={r} className="flex items-center gap-2">
                    <span className="flex w-7 shrink-0 items-center gap-0.5 text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
                      {r}<Star className="h-3 w-3" fill={RATING_COLOR[r]} style={{ color: RATING_COLOR[r] }} />
                    </span>
                    <div className="relative h-2.5 flex-1 overflow-hidden rounded-full" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                      <div className="absolute inset-y-0 left-0 rounded-full transition-[width] duration-500" style={{ width: `${w}%`, backgroundColor: RATING_COLOR[r] }} />
                    </div>
                    <span className="w-14 shrink-0 text-right text-xs tabular-nums" style={{ color: 'var(--text-tertiary)' }}>{count} · {pct}%</span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Average trend */}
          <div className="flex flex-col">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--text-tertiary)' }}>{trendLabel}</p>
            {hasTrend ? (
              <div className="flex-1" style={{ height: '200px' }}>
                {/* initialDimension: ilk frame'de Recharts default -1x-1 olcumuyle uyari
                    basmasin diye container'in gercek yuksekligini (200px) baslangic veriyoruz. */}
                <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: 400, height: 200 }}>
                  <LineChart data={trend} margin={{ top: 10, right: 8, left: -16, bottom: 6 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
                    <XAxis dataKey="dateLabel" tick={{ fill: CHART_COLORS.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} interval="preserveStartEnd" minTickGap={20} />
                    <YAxis domain={[0, 5]} ticks={[1, 2, 3, 4, 5]} tick={{ fill: CHART_COLORS.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: CHART_COLORS.grid }} width={28} />
                    <Tooltip content={<TrendTooltip />} />
                    <Line type="monotone" dataKey="avg" stroke="#f59e0b" strokeWidth={2.5} dot={{ r: 2 }} activeDot={{ r: 5 }} connectNulls />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="flex flex-1 items-center justify-center rounded-2xl border border-dashed py-10 text-center text-xs" style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}>
                {emptyText}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

export default memo(AgentCsatChart);
