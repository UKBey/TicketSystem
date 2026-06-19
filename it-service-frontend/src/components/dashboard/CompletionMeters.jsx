import { memo, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, ShieldCheck } from 'lucide-react';
import Skeleton from '../Skeleton';
import { getCompletionColor } from './ChartColors';

const METERS = [
  {
    key: 'completionRate',
    labelKey: 'ticketCompletion',
    icon: CheckCircle2,
    detail: (r, t) =>
      t('dashboard.completionMeters.completionDetail', {
        completed: (r.totalResolved ?? 0) + (r.totalClosed ?? 0),
        created: r.totalCreated ?? 0,
      }),
  },
  {
    key: 'slaComplianceRate',
    labelKey: 'slaCompliance',
    icon: ShieldCheck,
    detail: (r, t) =>
      t('dashboard.completionMeters.slaDetail', { count: r.resolvedInPeriod ?? r.totalResolved ?? 0 }),
  },
];

function CompletionMeters({ data, loading }) {
  const { t } = useTranslation();
  const rates              = data?.completionRates ?? {};
  const avgResolutionHours = rates.avgResolutionHours ?? 0;

  // Oranların arkasındaki ham sayılar — hover'da tooltip içinde gösterilir.
  const totalResolved     = rates.totalResolved ?? 0;
  const totalClosed       = rates.totalClosed ?? 0;
  const totalCreated      = rates.totalCreated ?? 0;
  const completed         = totalResolved + totalClosed;
  const stillOpen         = Math.max(0, totalCreated - completed);
  const resolvedInPeriod  = rates.resolvedInPeriod ?? rates.totalResolved ?? 0;
  const slaRate           = rates.slaComplianceRate ?? 0;
  // SLA "karşılandı / ihlal" dökümü orandan türetilir (backend yalnızca oran + payda döndürür).
  const slaMet            = Math.round((resolvedInPeriod * slaRate) / 100);
  const slaBreached       = Math.max(0, resolvedInPeriod - slaMet);

  // Hover/odak durumu — ölçer satırları ile ort. çözüm kutusu arasında paylaşılır.
  // { key, x, y } — x null ise yalnızca vurgu yapılır (tooltip için konum yok).
  const [active, setActive] = useState(null);
  const wrapRef = useRef(null);

  // Tooltip'i tetikleyen elemanın (satır/kutu) ortasına, hemen üstüne sabitler — fare ve klavye için aynı.
  const activate = (key) => (e) => {
    if (loading) return;
    const wrap = wrapRef.current?.getBoundingClientRect();
    const el = e.currentTarget.getBoundingClientRect();
    setActive(wrap
      ? { key, x: el.left - wrap.left + el.width / 2, y: el.top - wrap.top }
      : { key, x: null, y: null });
  };
  const clear = () => setActive(null);

  const TIP = {
    completionRate: {
      title: t('dashboard.completionMeters.ticketCompletion'),
      big: `${(rates.completionRate ?? 0).toFixed(1)}%`,
      accent: getCompletionColor(rates.completionRate ?? 0),
      rows: [
        { label: t('dashboard.completionMeters.rowResolved'), value: totalResolved },
        { label: t('dashboard.completionMeters.rowClosed'), value: totalClosed },
        { label: t('dashboard.completionMeters.rowStillOpen'), value: stillOpen },
        { label: t('dashboard.completionMeters.rowCreated'), value: totalCreated },
      ],
    },
    slaComplianceRate: {
      title: t('dashboard.completionMeters.slaCompliance'),
      big: `${slaRate.toFixed(1)}%`,
      accent: getCompletionColor(slaRate),
      rows: [
        { label: t('dashboard.completionMeters.rowMetSla'), value: slaMet },
        { label: t('dashboard.completionMeters.rowBreached'), value: slaBreached },
        { label: t('dashboard.completionMeters.rowResolved'), value: resolvedInPeriod },
      ],
    },
    avg: {
      title: t('dashboard.completionMeters.avgResolution'),
      big: `${avgResolutionHours.toFixed(1)}${t('dashboard.units.hour')}`,
      accent: 'var(--text-primary)',
      rows: [{ label: t('dashboard.completionMeters.across'), value: t('dashboard.completionMeters.acrossResolved', { count: resolvedInPeriod }) }],
    },
  };
  const tip = active ? TIP[active.key] : null;

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <h2 className="mb-4 text-sm font-bold uppercase tracking-[0.18em]"
        style={{ color: 'var(--text-secondary)' }}>
        {t('dashboard.completionMeters.title')}
      </h2>

      <div ref={wrapRef} className="relative">
        <div className="space-y-4">
          {METERS.map(({ key, labelKey, icon: Icon, detail }) => {
            const rate  = rates[key] ?? 0;
            const color = getCompletionColor(rate);
            const label = t('dashboard.completionMeters.' + labelKey);
            const isOn  = active?.key === key;
            const isDim = !!active && active.key !== key;
            return (
              <div
                key={key}
                onMouseEnter={loading ? undefined : activate(key)}
                onMouseLeave={clear}
                onFocus={loading ? undefined : activate(key)}
                onBlur={clear}
                tabIndex={loading ? undefined : 0}
                role="button"
                aria-label={t('dashboard.completionMeters.ariaMeter', { label, rate: rate.toFixed(1), detail: detail(rates, t) })}
                className="-mx-2 rounded-lg px-2 py-1.5 transition-all"
                style={{
                  backgroundColor: isOn ? 'var(--bg-surface-hover)' : 'transparent',
                  opacity: isDim ? 0.45 : 1,
                  cursor: loading ? 'default' : 'pointer',
                  outline: 'none',
                }}
              >
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
                    <Skeleton className="h-full w-full rounded-full"
                      style={{ backgroundColor: 'var(--border-color-light)' }} />
                  ) : (
                    <div className="completion-meter-fill absolute inset-y-0 left-0 rounded-full"
                      style={{
                        width: `${Math.min(rate, 100)}%`,
                        backgroundColor: color,
                        filter: isOn ? 'brightness(1.15) saturate(1.2)' : 'none',
                        transition: 'filter .15s ease',
                      }} />
                  )}
                </div>

                {!loading && (
                  <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                    {detail(rates, t)}
                  </p>
                )}
              </div>
            );
          })}
        </div>

        {/* Avg resolution stat — hover'da hangi payda üzerinden hesaplandığını gösterir */}
        <div
          onMouseEnter={loading ? undefined : activate('avg')}
          onMouseLeave={clear}
          onFocus={loading ? undefined : activate('avg')}
          onBlur={clear}
          tabIndex={loading ? undefined : 0}
          role="button"
          aria-label={t('dashboard.completionMeters.ariaAvg', { hours: avgResolutionHours.toFixed(1), count: resolvedInPeriod })}
          className="mt-4 flex items-center justify-between rounded-xl border px-4 py-2.5 transition-all"
          style={{
            backgroundColor: active?.key === 'avg' ? 'var(--bg-surface-hover)' : 'var(--bg-surface-secondary)',
            borderColor: 'var(--border-color-light)',
            opacity: active && active.key !== 'avg' ? 0.45 : 1,
            cursor: loading ? 'default' : 'pointer',
            outline: 'none',
          }}
        >
          <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
            {t('dashboard.completionMeters.avgResolution')}
          </span>
          <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            {loading ? '…' : `${avgResolutionHours.toFixed(1)}${t('dashboard.units.hour')}`}
          </span>
        </div>

        {/* Hover/odak tooltip — oranın arkasındaki sayısal döküm */}
        {tip && active.x !== null && (
          <div
            className="pointer-events-none absolute z-10 min-w-[10rem] rounded-lg border px-3 py-2 shadow-lg"
            style={{
              left: active.x,
              top: active.y,
              transform: 'translate(-50%, calc(-100% - 8px))',
              backgroundColor: 'var(--bg-surface)',
              borderColor: 'var(--border-color)',
            }}
          >
            <div className="mb-1 flex items-center justify-between gap-3">
              <span className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>{tip.title}</span>
              <span className="text-sm font-black" style={{ color: tip.accent }}>{tip.big}</span>
            </div>
            <div className="space-y-0.5">
              {tip.rows.map((r) => (
                <div key={r.label} className="flex items-center justify-between gap-4 text-[11px]">
                  <span style={{ color: 'var(--text-tertiary)' }}>{r.label}</span>
                  <span className="font-semibold" style={{ color: 'var(--text-secondary)' }}>{r.value}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

    </section>
  );
}

export default memo(CompletionMeters);
