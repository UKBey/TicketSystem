import { memo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart } from 'lucide-react';
import { STATUS_COLORS } from '../../constants/ticketColors';

const STATUS_CONFIG = [
  { key: 'newCount', statusKey: 'new', color: STATUS_COLORS.NEW.solid, descKey: 'descNew' },
  { key: 'inProgressCount', statusKey: 'in_progress', color: STATUS_COLORS.IN_PROGRESS.solid, descKey: 'descInProgress' },
  { key: 'waitingForCustomerCount', statusKey: 'waiting_for_customer', color: STATUS_COLORS.WAITING_FOR_CUSTOMER.solid, descKey: 'descWaiting' },
  { key: 'resolvedCount', statusKey: 'resolved', color: STATUS_COLORS.RESOLVED.solid, descKey: 'descResolved' },
  { key: 'closedCount', statusKey: 'closed', color: STATUS_COLORS.CLOSED.solid, descKey: 'descClosed' },
];

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
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
    x: cx + radius * Math.cos(angleInRadians),
    y: cy + radius * Math.sin(angleInRadians),
  };
}

function StatusDistributionChart({ data, loading }) {
  const { t } = useTranslation();
  // hoveredKey: active on hover, activeKey: pinned by click
  const [hoveredKey, setHoveredKey] = useState(null);
  const [activeKey, setActiveKey] = useState(null);

  // Active segment to display: click > hover
  const displayKey = activeKey ?? hoveredKey;

  const entries = STATUS_CONFIG.map((item) => ({
    ...item,
    label: t(`ticket.status.${item.statusKey}`),
    description: t(`dashboard.statusChart.${item.descKey}`),
    value: Number(data?.[item.key] ?? 0),
  }));

  const total = Number(data?.totalCount ?? entries.reduce((sum, item) => sum + item.value, 0));
  const radius = 74;
  const innerRadius = 44;

  const positiveEntries = entries.filter((item) => item.value > 0);
  const segments = positiveEntries.reduce((accumulator, item) => {
    const sliceAngle = total > 0 ? (item.value / total) * 360 : 0;
    const startAngle = accumulator.length === 0 ? 0 : accumulator[accumulator.length - 1].endAngle;
    accumulator.push({ ...item, startAngle, endAngle: startAngle + sliceAngle });
    return accumulator;
  }, []);

  const activeSegment = segments.find((s) => s.key === displayKey);
  const centerLabel = activeSegment ? activeSegment.label : t('dashboard.statusChart.total');
  const centerValue = activeSegment ? activeSegment.value : total;

  const handleSegmentClick = (key) => {
    setActiveKey((prev) => (prev === key ? null : key));
  };

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5 flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-start sm:gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <PieChart className="h-3.5 w-3.5" />
            {t('dashboard.statusChart.badge')}
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.statusChart.title')}</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('dashboard.statusChart.subtitle')}
          </p>
        </div>
        <div className="rounded-2xl px-3 py-2 text-right" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
          <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.statusChart.total')}</div>
          <div className="text-xl font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(total)}</div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[220px_1fr] lg:items-center">
        {/* Pie chart */}
        <div className="flex justify-center">
          <div className="relative h-[180px] w-[180px] sm:h-[220px] sm:w-[220px]">
            <svg viewBox="0 0 220 220" className="h-full w-full">
              <defs>
                <filter id="status-chart-shadow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="8" stdDeviation="10" floodColor="rgba(15, 23, 42, 0.14)" />
                </filter>
              </defs>

              {/* Background ring */}
              <circle cx="110" cy="110" r={radius} fill="none" stroke="var(--bg-surface-secondary)" strokeWidth="28" />

              {loading ? (
                <circle cx="110" cy="110" r={radius} fill="none" stroke="var(--text-tertiary)" strokeWidth="28" strokeDasharray="35 12" opacity="0.35" />
              ) : (
                segments.map((segment) => {
                  const isActive = segment.key === displayKey;
                  const isDimmed = displayKey && !isActive;
                  return (
                    <path
                      key={segment.key}
                      d={buildArcPath(110, 110, radius, innerRadius, segment.startAngle, segment.endAngle)}
                      fill={segment.color}
                      filter="url(#status-chart-shadow)"
                      opacity={isDimmed ? 0.3 : 1}
                      stroke={isActive ? 'white' : 'none'}
                      strokeWidth={isActive ? 2 : 0}
                      style={{
                        cursor: 'pointer',
                        transition: 'opacity 0.2s ease',
                        outline: 'none',
                      }}
                      onClick={() => handleSegmentClick(segment.key)}
                      onMouseEnter={() => setHoveredKey(segment.key)}
                      onMouseLeave={() => setHoveredKey(null)}
                      role="button"
                      tabIndex={0}
                      aria-label={`${segment.label}: ${formatNumber(segment.value)}`}
                      onKeyDown={(e) => e.key === 'Enter' && handleSegmentClick(segment.key)}
                    />
                  );
                })
              )}

              {/* Inner circle */}
              <circle cx="110" cy="110" r={innerRadius} fill="var(--bg-surface)" style={{ pointerEvents: 'none' }} />
            </svg>

            {/* Center text */}
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
              <span
                className="text-[10px] font-semibold uppercase tracking-[0.15em] truncate max-w-[80px]"
                style={{ color: activeSegment ? activeSegment.color : 'var(--text-tertiary)' }}
              >
                {centerLabel}
              </span>
              <span className="mt-1 text-3xl font-black" style={{ color: 'var(--text-primary)' }}>
                {loading ? '...' : formatNumber(centerValue)}
              </span>
              <span className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                {activeSegment
                  ? `%${total > 0 ? Math.round((activeSegment.value / total) * 100) : 0}`
                  : t('dashboard.statusChart.ticket')}
              </span>
            </div>
          </div>
        </div>

        {/* Right list */}
        <div className="space-y-3">
          {(loading ? entries : segments.length > 0 ? segments : entries).map((item) => {
            const value = item.value ?? 0;
            const percentage = total > 0 ? Math.round((value / total) * 100) : 0;
            const isActive = item.key === displayKey;
            const isDimmed = displayKey && !isActive;

            return (
              <div
                key={item.key}
                className="rounded-2xl border px-4 py-3 cursor-pointer transition-all duration-200"
                style={{
                  backgroundColor: isActive ? `${item.color}18` : 'var(--bg-surface-secondary)',
                  borderColor: isActive ? item.color : 'var(--border-color-light)',
                  opacity: isDimmed ? 0.45 : 1,
                  transform: isActive ? 'translateX(4px)' : 'none',
                }}
                onClick={() => handleSegmentClick(item.key)}
                onMouseEnter={() => setHoveredKey(item.key)}
                onMouseLeave={() => setHoveredKey(null)}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span
                        className="h-2.5 w-2.5 rounded-full transition-transform duration-200"
                        style={{
                          backgroundColor: item.color,
                          transform: isActive ? 'scale(1.4)' : 'scale(1)',
                        }}
                      />
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
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{ width: `${percentage}%`, backgroundColor: item.color }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

export default memo(StatusDistributionChart);
