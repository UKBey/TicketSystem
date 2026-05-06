import { memo } from 'react';
import { MessageSquare, Star } from 'lucide-react';

const CX = 110;
const CY = 118;
const OUTER_R = 92;
const INNER_R = 66;
const MID_R = (OUTER_R + INNER_R) / 2;

const ZONES = [
  { score: 1, color: '#ef4444', startAngle: 270, endAngle: 306 },
  { score: 2, color: '#f97316', startAngle: 306, endAngle: 342 },
  { score: 3, color: '#f59e0b', startAngle: 342, endAngle:  18 },
  { score: 4, color: '#84cc16', startAngle:  18, endAngle:  54 },
  { score: 5, color: '#22c55e', startAngle:  54, endAngle:  90 },
];

const PRIORITY_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const PRIORITY_COLORS = {
  CRITICAL: { bg: 'rgba(239,68,68,0.10)',  border: 'rgba(239,68,68,0.25)',  text: '#b91c1c' },
  HIGH:     { bg: 'rgba(249,115,22,0.10)', border: 'rgba(249,115,22,0.25)', text: '#c2410c' },
  MEDIUM:   { bg: 'rgba(245,158,11,0.10)', border: 'rgba(245,158,11,0.25)', text: '#b45309' },
  LOW:      { bg: 'rgba(34,197,94,0.10)',  border: 'rgba(34,197,94,0.25)',  text: '#15803d' },
};

const TREND_CONFIG = {
  UP:     { icon: '↑', label: 'yükseliyor', bg: 'rgba(34,197,94,0.10)',  border: 'rgba(34,197,94,0.30)',  color: '#15803d' },
  DOWN:   { icon: '↓', label: 'düşüyor',    bg: 'rgba(239,68,68,0.10)',  border: 'rgba(239,68,68,0.30)',  color: '#b91c1c' },
  STABLE: { icon: '→', label: 'stabil',     bg: 'rgba(148,163,184,0.10)', border: 'rgba(148,163,184,0.3)', color: '#64748b' },
};

// Convention: 0 = top, clockwise positive (matches StatusDistributionChart)
function polarToCartesian(cx, cy, r, deg) {
  const rad = (deg - 90) * Math.PI / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

function buildArcPath(cx, cy, outerR, innerR, startDeg, endDeg) {
  const os = polarToCartesian(cx, cy, outerR, endDeg);
  const oe = polarToCartesian(cx, cy, outerR, startDeg);
  const is_ = polarToCartesian(cx, cy, innerR, endDeg);
  const ie = polarToCartesian(cx, cy, innerR, startDeg);
  const la = endDeg - startDeg <= 180 ? '0' : '1';
  return [
    `M ${os.x} ${os.y}`,
    `A ${outerR} ${outerR} 0 ${la} 0 ${oe.x} ${oe.y}`,
    `L ${ie.x} ${ie.y}`,
    `A ${innerR} ${innerR} 0 ${la} 1 ${is_.x} ${is_.y}`,
    'Z',
  ].join(' ');
}

function getScoreColor(score) {
  if (score >= 4.5) return '#22c55e';
  if (score >= 3.5) return '#84cc16';
  if (score >= 2.5) return '#f59e0b';
  if (score >= 1.5) return '#f97316';
  return '#ef4444';
}

function CSATGaugeChart({ data, loading }) {
  const avgRating = data?.averageRating ?? 0;
  const totalResponses = data?.totalResponses ?? 0;
  const distribution = data?.ratingDistribution ?? {};
  const trend = data?.trend ?? { thisMonth: 0, lastMonth: 0, trend: 'STABLE' };
  const byPriority = data?.byPriority ?? {};
  const topComments = data?.topComments ?? [];

  const scoreColor = getScoreColor(avgRating);
  const trendConf = TREND_CONFIG[trend.trend] ?? TREND_CONFIG.STABLE;

  // Gauge needle angle: score 1 → 270°, score 5 → 90°
  const indicatorAngle = 270 + ((Math.min(Math.max(avgRating, 1), 5) - 1) / 4) * 180;
  const indicatorPos = polarToCartesian(CX, CY, MID_R, indicatorAngle);

  const maxCount = Math.max(...Object.values(distribution).map(Number), 1);

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      {/* Header */}
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <Star className="h-3.5 w-3.5" />
            CSAT Analitik
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Müşteri memnuniyet skoru</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Puan dağılımı, aylık trend ve priority bazlı CSAT analizi.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {!loading && (
            <span className="inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold" style={{ backgroundColor: trendConf.bg, borderColor: trendConf.border, color: trendConf.color }}>
              {trendConf.icon} {trend.thisMonth.toFixed(2)} bu ay · {trendConf.label}
            </span>
          )}
          <div className="rounded-2xl px-3 py-2 text-right" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Yanıt</div>
            <div className="text-xl font-black" style={{ color: 'var(--text-primary)' }}>
              {loading ? '…' : totalResponses.toLocaleString('tr-TR')}
            </div>
          </div>
        </div>
      </div>

      {/* Gauge + Distribution */}
      <div className="grid gap-6 lg:grid-cols-2 lg:items-center">

        {/* Gauge SVG */}
        <div className="flex justify-center">
          <svg viewBox="0 0 220 122" className="w-full max-w-[260px]" aria-label={`CSAT skoru: ${avgRating.toFixed(1)} / 5`}>

            {/* Background arc */}
            <path
              d={buildArcPath(CX, CY, OUTER_R, INNER_R, 270, 90)}
              fill="var(--bg-surface-secondary)"
            />

            {loading ? (
              <path
                d={buildArcPath(CX, CY, OUTER_R, INNER_R, 270, 90)}
                fill="var(--text-tertiary)"
                opacity="0.25"
              />
            ) : (
              <>
                {ZONES.map((zone) => (
                  <path
                    key={zone.score}
                    d={buildArcPath(CX, CY, OUTER_R, INNER_R, zone.startAngle, zone.endAngle)}
                    fill={zone.color}
                  />
                ))}
                {/* Score indicator dot */}
                <circle
                  cx={indicatorPos.x}
                  cy={indicatorPos.y}
                  r="8"
                  fill="white"
                  stroke={scoreColor}
                  strokeWidth="3.5"
                />
              </>
            )}

            {/* Score text */}
            {loading ? (
              <rect x="76" y="82" width="68" height="24" rx="6" fill="var(--bg-surface-secondary)" opacity="0.6" />
            ) : (
              <>
                <text x={CX} y="102" textAnchor="middle" fontSize="32" fontWeight="900" style={{ fill: scoreColor }}>
                  {avgRating.toFixed(1)}
                </text>
                <text x={CX + 30} y="102" textAnchor="middle" fontSize="13" fontWeight="600" style={{ fill: 'var(--text-tertiary)' }}>
                  /5
                </text>
              </>
            )}
          </svg>
        </div>

        {/* Distribution bars */}
        <div className="space-y-2.5">
          {loading ? (
            [1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="flex items-center gap-3">
                <div className="h-4 w-6 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
                <div className="h-3 flex-1 animate-pulse rounded-full" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
                <div className="h-4 w-14 animate-pulse rounded" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
              </div>
            ))
          ) : (
            [5, 4, 3, 2, 1].map((rating) => {
              const zone = ZONES.find((z) => z.score === rating);
              const count = Number(distribution[rating] ?? 0);
              const pct = totalResponses > 0 ? Math.round((count / totalResponses) * 100) : 0;
              const barWidth = (count / maxCount) * 100;

              return (
                <div key={rating} className="flex items-center gap-3">
                  <div className="flex w-7 shrink-0 items-center gap-0.5">
                    <span className="text-sm font-bold leading-none" style={{ color: 'var(--text-secondary)' }}>{rating}</span>
                    <Star className="h-3 w-3 shrink-0" style={{ color: zone.color, fill: zone.color }} />
                  </div>
                  <div className="flex-1 overflow-hidden rounded-full" style={{ height: '10px', backgroundColor: 'var(--bg-surface-secondary)' }}>
                    <div className="csat-dist-bar h-full rounded-full" style={{ width: `${barWidth}%`, backgroundColor: zone.color }} />
                  </div>
                  <div className="w-16 shrink-0 text-right">
                    <span className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>{count}</span>
                    <span className="ml-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>({pct}%)</span>
                  </div>
                </div>
              );
            })
          )}

          {/* Month comparison */}
          {!loading && trend.lastMonth > 0 && (
            <div className="mt-1 flex items-center gap-2 rounded-xl border px-3 py-2 text-xs" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
              <span style={{ color: 'var(--text-tertiary)' }}>Geçen ay</span>
              <span className="font-bold" style={{ color: 'var(--text-primary)' }}>{trend.lastMonth.toFixed(2)}</span>
              <span className="ml-auto" style={{ color: 'var(--text-tertiary)' }}>Bu ay</span>
              <span className="font-bold" style={{ color: trendConf.color }}>{trend.thisMonth.toFixed(2)}</span>
            </div>
          )}
        </div>
      </div>

      {/* Priority breakdown */}
      {!loading && Object.keys(byPriority).length > 0 && (
        <div className="mt-6">
          <p className="mb-3 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>
            Priority bazlı CSAT
          </p>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PRIORITY_ORDER.filter((p) => byPriority[p]).map((priority) => {
              const item = byPriority[priority];
              const c = PRIORITY_COLORS[priority] ?? PRIORITY_COLORS.LOW;
              return (
                <div key={priority} className="rounded-xl border px-3 py-2.5 text-center" style={{ backgroundColor: c.bg, borderColor: c.border }}>
                  <div className="text-[11px] font-semibold uppercase tracking-[0.15em]" style={{ color: c.text }}>
                    {priority}
                  </div>
                  <div className="mt-1 text-xl font-black" style={{ color: 'var(--text-primary)' }}>
                    {Number(item.avg).toFixed(1)}
                  </div>
                  <div className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                    {item.responses} yanıt
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Top comments */}
      {!loading && topComments.length > 0 && (
        <div className="mt-6">
          <p className="mb-3 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>
            <MessageSquare className="h-3.5 w-3.5" />
            Son yüksek puanlı yorumlar
          </p>
          <div className="flex flex-wrap gap-2">
            {topComments.map((comment, i) => (
              <span
                key={i}
                className="inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium"
                style={{ backgroundColor: 'rgba(34,197,94,0.08)', borderColor: 'rgba(34,197,94,0.2)', color: 'var(--text-secondary)' }}
              >
                "{comment.length > 45 ? `${comment.substring(0, 45)}…` : comment}"
              </span>
            ))}
          </div>
        </div>
      )}

    </section>
  );
}

export default memo(CSATGaugeChart);
