import { memo } from 'react';
import { PieChart } from 'lucide-react';

const STATUS_CONFIG = [
  { key: 'newCount', label: 'NEW', color: '#3b82f6', description: 'Bekleyen kayıtlar' },
  { key: 'inProgressCount', label: 'IN_PROGRESS', color: '#f59e0b', description: 'Üzerinde çalışılanlar' },
  { key: 'waitingForCustomerCount', label: 'WAITING_FOR_CUSTOMER', color: '#8b5cf6', description: 'Müşteri yanıtı bekleyenler' },
  { key: 'resolvedCount', label: 'RESOLVED', color: '#22c55e', description: 'Çözüme ulaşanlar' },
  { key: 'closedCount', label: 'CLOSED', color: '#64748b', description: 'Kapanan kayıtlar' },
];

function formatNumber(value) {
  return new Intl.NumberFormat('tr-TR').format(value ?? 0);
}

function buildArcPath(cx, cy, radius, innerRadius, startAngle, endAngle) {
  const start = polarToCartesian(cx, cy, radius, endAngle);
  const end = polarToCartesian(cx, cy, radius, startAngle);
  const innerStart = polarToCartesian(cx, cy, innerRadius, endAngle);
  const innerEnd = polarToCartesian(cx, cy, innerRadius, startAngle);
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';

  return [
    `M ${start.x} ${start.y}`,
    `A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`,
    `L ${innerEnd.x} ${innerEnd.y}`,
    `A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 1 ${innerStart.x} ${innerStart.y}`,
    'Z',
  ].join(' ');
}

function polarToCartesian(cx, cy, radius, angleInDegrees) {
  const angleInRadians = (angleInDegrees - 90) * Math.PI / 180.0;

  return {
    x: cx + (radius * Math.cos(angleInRadians)),
    y: cy + (radius * Math.sin(angleInRadians)),
  };
}

function StatusDistributionChart({ data, loading }) {
  const entries = STATUS_CONFIG.map((item) => ({
    ...item,
    value: Number(data?.[item.key] ?? 0),
  }));

  const total = Number(data?.totalCount ?? entries.reduce((sum, item) => sum + item.value, 0));
  const radius = 74;
  const innerRadius = 44;

  const positiveEntries = entries.filter((item) => item.value > 0);
  const segments = positiveEntries.reduce((accumulator, item) => {
    const sliceAngle = total > 0 ? (item.value / total) * 360 : 0;
    const startAngle = accumulator.length === 0 ? 0 : accumulator[accumulator.length - 1].endAngle;

    accumulator.push({
      ...item,
      startAngle,
      endAngle: startAngle + sliceAngle,
    });

    return accumulator;
  }, []);

  const activeSegment = segments[0];

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <PieChart className="h-3.5 w-3.5" />
            Status dağılımı
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Ticket durumları tek bakışta</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED ve CLOSED sayımları.
          </p>
        </div>
        <div className="rounded-2xl px-3 py-2 text-right" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
          <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Toplam</div>
          <div className="text-xl font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(total)}</div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[220px_1fr] lg:items-center">
        <div className="flex justify-center">
          <div className="relative h-[220px] w-[220px]">
            <svg viewBox="0 0 220 220" className="h-full w-full">
              <defs>
                <filter id="status-chart-shadow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="8" stdDeviation="10" floodColor="rgba(15, 23, 42, 0.14)" />
                </filter>
              </defs>

              <circle cx="110" cy="110" r={radius} fill="none" stroke="var(--bg-surface-secondary)" strokeWidth="28" />

              {loading ? (
                <circle cx="110" cy="110" r={radius} fill="none" stroke="var(--text-tertiary)" strokeWidth="28" strokeDasharray="35 12" opacity="0.35" />
              ) : (
                segments.map((segment) => (
                  <path
                    key={segment.key}
                    d={buildArcPath(110, 110, radius, innerRadius, segment.startAngle, segment.endAngle)}
                    fill={segment.color}
                    filter="url(#status-chart-shadow)"
                  />
                ))
              )}

              <circle cx="110" cy="110" r={innerRadius} fill="var(--bg-surface)" />
            </svg>

            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
              <span className="text-[11px] font-semibold uppercase tracking-[0.2em]" style={{ color: 'var(--text-tertiary)' }}>Durum</span>
              <span className="mt-1 text-3xl font-black" style={{ color: 'var(--text-primary)' }}>
                {loading ? '...' : formatNumber(total)}
              </span>
              <span className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>ticket</span>
            </div>
          </div>
        </div>

        <div className="space-y-3">
          {(loading ? entries : segments.length > 0 ? segments : entries).map((item) => {
            const value = item.value ?? 0;
            const percentage = total > 0 ? Math.round((value / total) * 100) : 0;

            return (
              <div key={item.key} className="rounded-2xl border px-4 py-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                      <span className="text-sm font-bold tracking-wide" style={{ color: 'var(--text-primary)' }}>{item.label}</span>
                    </div>
                    <p className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>{item.description}</p>
                  </div>
                  <div className="text-right">
                    <div className="text-sm font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(value)}</div>
                    <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>%{percentage}</div>
                  </div>
                </div>
                <div className="mt-3 h-2 overflow-hidden rounded-full" style={{ backgroundColor: 'var(--bg-surface)' }}>
                  <div className="h-full rounded-full" style={{ width: `${percentage}%`, backgroundColor: item.color }} />
                </div>
              </div>
            );
          })}
          <div className="rounded-2xl border px-4 py-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
            <div className="text-xs uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Öncelikli durum</div>
            <div className="mt-1 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
              {activeSegment ? `${activeSegment.label} ilk segment olarak öne çıkıyor.` : 'Veri yok.'}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default memo(StatusDistributionChart);