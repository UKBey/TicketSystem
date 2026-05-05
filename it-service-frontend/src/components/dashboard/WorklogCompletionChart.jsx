import { Clock } from 'lucide-react';
import { getCompletionColor } from './ChartColors';

const CX = 80, CY = 80, R = 52, STROKE_W = 14;
const CIRCUMFERENCE = 2 * Math.PI * R;

function LegendRow({ color, label, value }) {
  return (
    <div className="flex items-center gap-2">
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
        <div className="mx-auto mt-1 h-5 w-12 animate-pulse rounded" style={{ backgroundColor: 'var(--border-color-light)' }} />
      ) : (
        <div className="mt-0.5 text-lg font-black" style={{ color: 'var(--text-primary)' }}>{value}</div>
      )}
    </div>
  );
}

export default function WorklogCompletionChart({ data, loading }) {
  const periodDays      = data?.periodDays ?? 30;
  const agentWorklogs   = data?.agentWorklogs ?? [];
  const completionRates = data?.completionRates ?? {};

  const totalMinutes  = agentWorklogs.reduce((s, a) => s + a.totalMinutes, 0);
  const totalHours    = totalMinutes / 60;
  const totalEntries  = agentWorklogs.reduce((s, a) => s + a.totalEntries, 0);
  const completionRate = completionRates.completionRate ?? 0;
  const eksikRate      = Math.max(0, 100 - completionRate);
  const color          = getCompletionColor(completionRate);

  const doluArc  = (Math.min(completionRate, 100) / 100) * CIRCUMFERENCE;
  const eksikArc = (eksikRate / 100) * CIRCUMFERENCE;

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <div className="mb-5">
        <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          <Clock className="h-3.5 w-3.5" />
          Worklog Özeti
        </div>
        <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
          Çalışma saati ve tamamlanma
        </h2>
        <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
          Son {periodDays} günün kayıtlı worklog ve bilet tamamlanma analizi.
        </p>
      </div>

      <div className="flex items-center gap-6">

        {/* Donut */}
        <div className="shrink-0">
          <svg width="160" height="160" viewBox="0 0 160 160"
            aria-label={`Tamamlanma oranı: ${completionRate.toFixed(1)}%`}>

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
                  <circle cx={CX} cy={CY} r={R} fill="none" strokeWidth={STROKE_W}
                    stroke="rgba(239,68,68,0.18)"
                    strokeDasharray={`${eksikArc} ${CIRCUMFERENCE}`}
                    strokeDashoffset={-doluArc}
                    transform="rotate(-90 80 80)" />
                )}
                <circle cx={CX} cy={CY} r={R} fill="none" strokeWidth={STROKE_W}
                  stroke={color}
                  strokeDasharray={`${doluArc} ${CIRCUMFERENCE}`}
                  transform="rotate(-90 80 80)" />
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
                  {totalHours.toFixed(0)}sa
                </text>
                <text x={CX} y={CY + 8} textAnchor="middle" fontSize="10"
                  style={{ fill: 'var(--text-tertiary)' }}>
                  {periodDays} günde
                </text>
                <text x={CX} y={CY + 24} textAnchor="middle" fontSize="10"
                  style={{ fill: 'var(--text-tertiary)' }}>
                  {totalEntries} giriş
                </text>
              </>
            )}
          </svg>
        </div>

        {/* Stats */}
        <div className="flex-1 space-y-3">
          <LegendRow
            color={color}
            label="Tamamlanan"
            value={loading ? '…' : `${completionRate.toFixed(1)}%`}
          />
          <LegendRow
            color="rgba(239,68,68,0.45)"
            label="Eksik"
            value={loading ? '…' : `${eksikRate.toFixed(1)}%`}
          />
          <div className="mt-1 grid grid-cols-2 gap-2">
            <StatCard label="Toplam giriş" value={loading ? null : totalEntries} />
            <StatCard label="Toplam saat"  value={loading ? null : `${totalHours.toFixed(1)}sa`} />
          </div>
        </div>
      </div>

    </section>
  );
}
