import { memo } from 'react';
import { CheckCircle2, ShieldCheck } from 'lucide-react';
import { getCompletionColor } from './ChartColors';

const METERS = [
  {
    key: 'completionRate',
    label: 'Bilet Tamamlanma',
    icon: CheckCircle2,
    detail: (r) =>
      `${(r.totalResolved ?? 0) + (r.totalClosed ?? 0)} / ${r.totalCreated ?? 0} bilet`,
  },
  {
    key: 'slaComplianceRate',
    label: 'SLA Uyum',
    icon: ShieldCheck,
    detail: (r) => `${r.totalResolved ?? 0} çözülen bilette`,
  },
];

function CompletionMeters({ data, loading }) {
  const rates             = data?.completionRates ?? {};
  const avgResolutionHours = rates.avgResolutionHours ?? 0;

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <h2 className="mb-4 text-sm font-bold uppercase tracking-[0.18em]"
        style={{ color: 'var(--text-secondary)' }}>
        Tamamlanma Göstergeleri
      </h2>

      <div className="space-y-5">
        {METERS.map(({ key, label, icon: Icon, detail }) => {
          const rate  = rates[key] ?? 0;
          const color = getCompletionColor(rate);
          return (
            <div key={key}>
              <div className="mb-1.5 flex items-center gap-2">
                <Icon className="h-3.5 w-3.5 shrink-0" style={{ color }} />
                <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                  {label}
                </span>
                <span className="ml-auto text-sm font-bold" style={{ color }}>
                  {loading ? '…' : `${rate.toFixed(1)}%`}
                </span>
              </div>

              <div className="relative h-2 overflow-hidden rounded-full"
                style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                {loading ? (
                  <div className="h-full w-full animate-pulse rounded-full"
                    style={{ backgroundColor: 'var(--border-color-light)' }} />
                ) : (
                  <div className="completion-meter-fill absolute inset-y-0 left-0 rounded-full"
                    style={{ width: `${Math.min(rate, 100)}%`, backgroundColor: color }} />
                )}
              </div>

              {!loading && (
                <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                  {detail(rates)}
                </p>
              )}
            </div>
          );
        })}
      </div>

      {/* Avg resolution stat */}
      <div className="mt-5 flex items-center justify-between rounded-xl border px-4 py-2.5"
        style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
        <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
          Ort. çözüm süresi
        </span>
        <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
          {loading ? '…' : `${avgResolutionHours.toFixed(1)} sa`}
        </span>
      </div>

    </section>
  );
}

export default memo(CompletionMeters);
