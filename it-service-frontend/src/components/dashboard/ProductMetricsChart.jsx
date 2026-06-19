import { memo, useState, useMemo } from 'react';
import { Package } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import Skeleton from '../Skeleton';
import { PRODUCT_COLORS } from './ChartColors';
import { localizedName } from '../../utils/localizedName';
import './dashboard.css';

const TOP_N = 6;

function fmt1(v) {
  return v != null ? Number(v).toFixed(1) : '—';
}

function ProductMetricsChart({ data, loading, onProductClick }) {
  const { t, i18n } = useTranslation();
  const [hoveredIdx, setHoveredIdx] = useState(null);

  const { rows, otherRow, maxTotal } = useMemo(() => {
    // productName, satır bazında aktif UI diline çözülür (dil değişiminde yeniden hesaplanır).
    const items = (data?.productMetrics ?? []).map((p) => ({ ...p, productName: localizedName(p, 'productName') }));
    const sorted = [...items].sort((a, b) => (b.totalTickets ?? 0) - (a.totalTickets ?? 0));
    const top = sorted.slice(0, TOP_N);
    const rest = sorted.slice(TOP_N);
    const max = top[0]?.totalTickets ?? 1;

    let otherRow = null;
    if (rest.length > 0) {
      const otherTotal = rest.reduce((s, p) => s + (p.totalTickets ?? 0), 0);
      const otherOpen  = rest.reduce((s, p) => s + (p.openTickets ?? 0), 0);
      otherRow = {
        productName: t('dashboard.productChart.other', { count: rest.length }),
        totalTickets: otherTotal,
        openTickets: otherOpen,
        avgResolutionHours: null,
        csatAverage: null,
        slaBreachPercentage: null,
        isOther: true,
      };
    }

    return { rows: top, otherRow, maxTotal: max };
    // localizedName aktif dili global i18n'den okur — dil değişince yeniden hesaplanmalı.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, i18n.language]);

  const totalTickets = useMemo(
    () => (data?.productMetrics ?? []).reduce((s, p) => s + (p.totalTickets ?? 0), 0),
    [data],
  );

  if (loading) {
    return (
      <div className="rounded-2xl border p-5 space-y-3" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <Skeleton className="h-4 w-40" />
        {[...Array(5)].map((_, i) => (
          <div key={i} className="space-y-1.5">
            <Skeleton className="h-3 w-28" />
            <Skeleton className="h-2 w-full" />
          </div>
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-2xl border p-8 flex flex-col items-center justify-center gap-2 text-center" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <Package className="h-8 w-8" style={{ color: 'var(--text-tertiary)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.productChart.empty')}</p>
      </div>
    );
  }

  const allRows = otherRow ? [...rows, otherRow] : rows;

  return (
    <div className="rounded-2xl border p-4 sm:p-5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-2 mb-5">
        <div className="min-w-0">
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('dashboard.productChart.title')}
          </h3>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
            {t('dashboard.productChart.subtitle', { count: totalTickets.toLocaleString('en-US'), top: Math.min(TOP_N, rows.length) })}
          </p>
        </div>
        <span
          className="inline-flex shrink-0 items-center rounded-full px-2.5 py-1 text-xs font-semibold"
          style={{ backgroundColor: 'rgba(59,130,246,0.10)', color: '#1d4ed8' }}
        >
          {t('dashboard.productChart.productsCount', { count: (data?.productMetrics ?? []).length })}
        </span>
      </div>

      {/* Rows */}
      <div className="space-y-4">
        {allRows.map((product, idx) => {
          const isOther = product.isOther;
          const color = PRODUCT_COLORS[isOther ? PRODUCT_COLORS.length - 1 : idx] ?? PRODUCT_COLORS[0];
          const pct = maxTotal > 0 ? ((product.totalTickets ?? 0) / maxTotal) * 100 : 0;
          const openPct = product.totalTickets > 0
            ? Math.round(((product.openTickets ?? 0) / product.totalTickets) * 100)
            : 0;

          const clickable = !isOther && onProductClick && product.productId;
          return (
            <div
              key={product.productName}
              className={`group relative ${clickable ? 'cursor-pointer' : ''}`}
              onMouseEnter={() => !isOther && setHoveredIdx(idx)}
              onMouseLeave={() => setHoveredIdx(null)}
              onClick={clickable ? () => onProductClick(product) : undefined}
              role={clickable ? 'button' : undefined}
              tabIndex={clickable ? 0 : undefined}
              onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onProductClick(product); } } : undefined}
            >
              {/* Name + count */}
              <div className="flex items-center justify-between mb-1.5">
                <span
                  className="text-xs font-semibold truncate max-w-[65%]"
                  style={{ color: isOther ? 'var(--text-tertiary)' : 'var(--text-primary)' }}
                  title={product.productName}
                >
                  {product.productName}
                </span>
                <div className="flex items-center gap-2 shrink-0">
                  <span className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                    {t('dashboard.productChart.openPct', { pct: openPct })}
                  </span>
                  <span
                    className="text-xs font-bold tabular-nums"
                    style={{ color: color.text }}
                  >
                    {(product.totalTickets ?? 0).toLocaleString('en-US')}
                  </span>
                </div>
              </div>

              {/* Bar */}
              <div className="product-bar-track">
                <div
                  className="product-bar-fill"
                  style={{ width: `${pct}%`, backgroundColor: color.bar }}
                />
              </div>

              {/* Hover tooltip */}
              {!isOther && hoveredIdx === idx && (
                <div className="product-tooltip">
                  <p className="font-bold mb-2 truncate" style={{ color: 'var(--text-primary)' }}>
                    {product.productName}
                  </p>
                  <div className="space-y-1" style={{ color: 'var(--text-secondary)' }}>
                    <div className="flex justify-between gap-4">
                      <span>{t('dashboard.productChart.tipTotal')}</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {(product.totalTickets ?? 0).toLocaleString('en-US')}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>{t('dashboard.productChart.tipOpen')}</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {(product.openTickets ?? 0).toLocaleString('en-US')}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>{t('dashboard.productChart.tipResolution')}</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {product.avgResolutionHours != null ? `${fmt1(product.avgResolutionHours)}${t('dashboard.units.hour')}` : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>{t('dashboard.productChart.tipCsat')}</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {product.csatAverage != null && product.csatAverage > 0 ? `${fmt1(product.csatAverage)} / 5` : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>{t('dashboard.productChart.tipSla')}</span>
                      <span
                        className="font-semibold"
                        style={{
                          color: (product.slaBreachPercentage ?? 0) > 10
                            ? 'var(--color-danger-600, #dc2626)'
                            : (product.slaBreachPercentage ?? 0) > 5
                              ? 'var(--color-warning-600, #d97706)'
                              : 'var(--color-accent-600, #16a34a)',
                        }}
                      >
                        {product.slaBreachPercentage != null ? `${fmt1(product.slaBreachPercentage)}%` : '—'}
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Color legend */}
      <div className="mt-5 pt-4 border-t flex flex-wrap gap-x-4 gap-y-2" style={{ borderColor: 'var(--border-color-light)' }}>
        {rows.map((product, idx) => {
          const color = PRODUCT_COLORS[idx] ?? PRODUCT_COLORS[0];
          return (
            <span key={product.productName} className="inline-flex items-center gap-1.5 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
              <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: color.bar }} />
              <span className="truncate max-w-[120px]" title={product.productName}>{product.productName}</span>
            </span>
          );
        })}
        {otherRow && (
          <span className="inline-flex items-center gap-1.5 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
            <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: PRODUCT_COLORS[PRODUCT_COLORS.length - 1].bar }} />
            {t('dashboard.productChart.otherShort')}
          </span>
        )}
      </div>
    </div>
  );
}

export default memo(ProductMetricsChart);
