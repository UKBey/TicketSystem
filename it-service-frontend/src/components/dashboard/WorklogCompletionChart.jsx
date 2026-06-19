import { memo, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Clock } from 'lucide-react';
import Skeleton from '../Skeleton';
import { getCompletionColor } from './ChartColors';

const CX = 80, CY = 80, R = 52, STROKE_W = 14;
const CIRCUMFERENCE = 2 * Math.PI * R;

function LegendRow({ color, label, value, active, dimmed, onEnter, onLeave }) {
  return (
    <div
      onMouseEnter={onEnter}
      onMouseLeave={onLeave}
      className="-mx-2 flex items-center gap-2 rounded-lg px-2 py-1 transition-colors"
      style={{
        backgroundColor: active ? 'var(--bg-surface-hover)' : 'transparent',
        opacity: dimmed ? 0.45 : 1,
        cursor: onEnter ? 'pointer' : 'default',
      }}
    >
      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />
      <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>{label}</span>
      <span className="ml-auto text-sm font-bold" style={{ color: 'var(--text-primary)' }}>{value}</span>
    </div>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="rounded-xl border px-3 py-2 text-center" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
      <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      {value === null ? (
        <Skeleton className="mx-auto mt-1 h-5 w-12" style={{ backgroundColor: 'var(--border-color-light)' }} />
      ) : (
        <div className="mt-0.5 text-lg font-black" style={{ color: 'var(--text-primary)' }}>{value}</div>
      )}
    </div>
  );
}

function WorklogCompletionChart({ data, loading }) {
  const { t } = useTranslation();
  const hourUnit = t('dashboard.units.hour');
  const periodDays      = data?.periodDays ?? 30;
  const agentWorklogs   = data?.agentWorklogs ?? [];
  const completionRates = data?.completionRates ?? {};

  const totalMinutes  = agentWorklogs.reduce((s, a) => s + a.totalMinutes, 0);
  const totalHours    = totalMinutes / 60;
  const totalEntries  = agentWorklogs.reduce((s, a) => s + a.totalEntries, 0);
  const completionRate = completionRates.completionRate ?? 0;
  const eksikRate      = Math.max(0, 100 - completionRate);
  const color          = getCompletionColor(completionRate);

  const filledArc  = (Math.min(completionRate, 100) / 100) * CIRCUMFERENCE;
  const missingArc = (eksikRate / 100) * CIRCUMFERENCE;

  // Halkanın yüzdelerinin arkasındaki gerçek ticket sayıları — hover'da gösterilir,
  // böylece "%X tamamlandı" soyut oran somut "X / Y ticket"e dönüşür.
  const completedCount  = (completionRates.totalResolved ?? 0) + (completionRates.totalClosed ?? 0);
  const createdCount    = completionRates.totalCreated ?? 0;
  const incompleteCount = Math.max(0, createdCount - completedCount);

  // Donut dilimleri ile legend satırları arasında paylaşılan hover/odak durumu.
  // { seg: 'completed'|'incomplete', x, y } — x null ise yalnızca vurgu yapılır, tooltip gösterilmez.
  const [active, setActive] = useState(null);
  const donutRef = useRef(null);

  const isActive = (seg) => active?.seg === seg;
  const dimmed   = (seg) => !!active && active.seg !== seg;
  const clear    = () => setActive(null);

  // Donut dilimi üzerinde fare — tooltip imlecin konumunda belirir.
  const point = (seg) => (e) => {
    if (loading) return;
    const rect = donutRef.current?.getBoundingClientRect();
    setActive(rect
      ? { seg, x: e.clientX - rect.left, y: e.clientY - rect.top }
      : { seg, x: CX, y: 40 });
  };
  // Legend hover — yalnızca vurgu (tooltip yok).
  const highlight = (seg) => () => { if (!loading) setActive({ seg, x: null, y: null }); };
  // Klavye odağı — tooltip'i halkanın üst-ortasında gösterir.
  const focusSeg = (seg) => () => { if (!loading) setActive({ seg, x: CX, y: 40 }); };

  const SEGMENTS = {
    completed:  { title: t('dashboard.worklogChart.completed'),  pct: completionRate, count: completedCount,  accent: color },
    incomplete: { title: t('dashboard.worklogChart.incomplete'), pct: eksikRate,      count: incompleteCount, accent: '#ef4444' },
  };
  const tip = active ? SEGMENTS[active.seg] : null;

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <div className="mb-5">
        <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          <Clock className="h-3.5 w-3.5" />
          {t('dashboard.worklogChart.badge')}
        </div>
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
          {t('dashboard.worklogChart.title')}
        </h2>
        <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
          {t('dashboard.worklogChart.subtitle', { days: periodDays })}
        </p>
      </div>

      <div className="flex flex-col items-center gap-4 sm:flex-row sm:gap-6">

        {/* Donut */}
        <div ref={donutRef} className="relative shrink-0">
          <svg width="160" height="160" viewBox="0 0 160 160"
            aria-label={t('dashboard.worklogChart.ariaCompletion', { rate: completionRate.toFixed(1) })}>

            {/* Track */}
            <circle cx={CX} cy={CY} r={R} fill="none" strokeWidth={STROKE_W}
              stroke="var(--bg-surface-secondary)" />

            {loading ? (
              <circle cx={CX} cy={CY} r={R} fill="none" strokeWidth={STROKE_W}
                stroke="var(--text-tertiary)" opacity="0.2"
                transform="rotate(-90 80 80)" />
            ) : (
              <>
                {eksikRate > 0.5 && (
                  <circle cx={CX} cy={CY} r={R} fill="none"
                    strokeWidth={isActive('incomplete') ? STROKE_W + 4 : STROKE_W}
                    stroke={isActive('incomplete') ? 'rgba(239,68,68,0.7)' : 'rgba(239,68,68,0.18)'}
                    strokeDasharray={`${missingArc} ${CIRCUMFERENCE}`}
                    strokeDashoffset={-filledArc}
                    transform="rotate(-90 80 80)"
                    opacity={dimmed('incomplete') ? 0.4 : 1}
                    style={{ cursor: 'pointer', transition: 'stroke-width .15s ease, opacity .15s ease' }}
                    role="button" tabIndex={0}
                    aria-label={t('dashboard.worklogChart.ariaIncomplete', { rate: eksikRate.toFixed(1), count: incompleteCount, total: createdCount })}
                    onMouseEnter={point('incomplete')} onMouseMove={point('incomplete')} onMouseLeave={clear}
                    onFocus={focusSeg('incomplete')} onBlur={clear} />
                )}
                <circle cx={CX} cy={CY} r={R} fill="none"
                  strokeWidth={isActive('completed') ? STROKE_W + 4 : STROKE_W}
                  stroke={color}
                  strokeDasharray={`${filledArc} ${CIRCUMFERENCE}`}
                  transform="rotate(-90 80 80)"
                  opacity={dimmed('completed') ? 0.4 : 1}
                  style={{ cursor: 'pointer', transition: 'stroke-width .15s ease, opacity .15s ease' }}
                  role="button" tabIndex={0}
                  aria-label={t('dashboard.worklogChart.ariaCompleted', { rate: completionRate.toFixed(1), count: completedCount, total: createdCount })}
                  onMouseEnter={point('completed')} onMouseMove={point('completed')} onMouseLeave={clear}
                  onFocus={focusSeg('completed')} onBlur={clear} />
              </>
            )}

            {/* Center text */}
            {loading ? (
              <rect x="28" y="56" width="104" height="48" rx="6"
                fill="var(--bg-surface-secondary)" opacity="0.5" />
            ) : (
              <>
                <text x={CX} y={CY - 8} textAnchor="middle" fontSize="20" fontWeight="900"
                  style={{ fill: color }}>
                  {totalHours.toFixed(0)}{hourUnit}
                </text>
                <text x={CX} y={CY + 8} textAnchor="middle" fontSize="10"
                  style={{ fill: 'var(--text-tertiary)' }}>
                  {t('dashboard.worklogChart.inDays', { days: periodDays })}
                </text>
                <text x={CX} y={CY + 24} textAnchor="middle" fontSize="10"
                  style={{ fill: 'var(--text-tertiary)' }}>
                  {t('dashboard.worklogChart.entries', { count: totalEntries })}
                </text>
              </>
            )}
          </svg>

          {/* Hover/focus tooltip — dilimin arkasındaki gerçek ticket sayısını gösterir */}
          {tip && active.x !== null && (
            <div
              className="pointer-events-none absolute z-10 whitespace-nowrap rounded-lg border px-3 py-2 text-center shadow-lg"
              style={{
                left: active.x,
                top: active.y,
                transform: 'translate(-50%, calc(-100% - 12px))',
                backgroundColor: 'var(--bg-surface)',
                borderColor: 'var(--border-color)',
              }}
            >
              <div className="flex items-center justify-center gap-1.5">
                <span className="h-2 w-2 rounded-full" style={{ backgroundColor: tip.accent }} />
                <span className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{tip.title}</span>
              </div>
              <div className="mt-0.5 text-lg font-black" style={{ color: tip.accent }}>{tip.pct.toFixed(1)}%</div>
              <div className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                {t('dashboard.worklogChart.tickets', { count: tip.count, total: createdCount })}
              </div>
            </div>
          )}
        </div>

        {/* Stats */}
        <div className="w-full flex-1 space-y-2">
          <LegendRow
            color={color}
            label={t('dashboard.worklogChart.completed')}
            value={loading ? '…' : `${completionRate.toFixed(1)}%`}
            active={isActive('completed')}
            dimmed={dimmed('completed')}
            onEnter={loading ? undefined : highlight('completed')}
            onLeave={clear}
          />
          <LegendRow
            color="rgba(239,68,68,0.45)"
            label={t('dashboard.worklogChart.incomplete')}
            value={loading ? '…' : `${eksikRate.toFixed(1)}%`}
            active={isActive('incomplete')}
            dimmed={dimmed('incomplete')}
            onEnter={loading ? undefined : highlight('incomplete')}
            onLeave={clear}
          />
          <div className="mt-1 grid grid-cols-2 gap-2">
            <StatCard label={t('dashboard.worklogChart.totalEntries')} value={loading ? null : totalEntries} />
            <StatCard label={t('dashboard.worklogChart.totalHours')}   value={loading ? null : `${totalHours.toFixed(1)}${hourUnit}`} />
          </div>
        </div>
      </div>

    </section>
  );
}

export default memo(WorklogCompletionChart);
