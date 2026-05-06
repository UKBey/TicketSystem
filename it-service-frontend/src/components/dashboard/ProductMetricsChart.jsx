import { memo, useState, useMemo } from 'react';
import { Package } from 'lucide-react';
import { PRODUCT_COLORS } from './ChartColors';
import './dashboard.css';

const TOP_N = 6;

function fmt1(v) {
  return v != null ? Number(v).toFixed(1) : '—';
}

function ProductMetricsChart({ data, loading }) {
  const [hoveredIdx, setHoveredIdx] = useState(null);

  const { rows, otherRow, maxTotal } = useMemo(() => {
    const items = data?.productMetrics ?? [];
    const sorted = [...items].sort((a, b) => (b.totalTickets ?? 0) - (a.totalTickets ?? 0));
    const top = sorted.slice(0, TOP_N);
    const rest = sorted.slice(TOP_N);
    const max = top[0]?.totalTickets ?? 1;

    let otherRow = null;
    if (rest.length > 0) {
      const otherTotal = rest.reduce((s, p) => s + (p.totalTickets ?? 0), 0);
      const otherOpen  = rest.reduce((s, p) => s + (p.openTickets ?? 0), 0);
      otherRow = {
        productName: `Diğer (${rest.length} ürün)`,
        totalTickets: otherTotal,
        openTickets: otherOpen,
        avgResolutionHours: null,
        csatAverage: null,
        slaBreachPercentage: null,
        isOther: true,
      };
    }

    return { rows: top, otherRow, maxTotal: max };
  }, [data]);

  const totalTickets = useMemo(
    () => (data?.productMetrics ?? []).reduce((s, p) => s + (p.totalTickets ?? 0), 0),
    [data],
  );

  if (loading) {
    return (
      <div className="rounded-2xl border p-5 space-y-3" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <div className="h-4 w-40 rounded bg-gray-200 dark:bg-gray-700/50 animate-pulse" />
        {[...Array(5)].map((_, i) => (
          <div key={i} className="space-y-1.5">
            <div className="h-3 w-28 rounded bg-gray-200 dark:bg-gray-700/50 animate-pulse" />
            <div className="h-2 w-full rounded bg-gray-200 dark:bg-gray-700/50 animate-pulse" />
          </div>
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-2xl border p-8 flex flex-col items-center justify-center gap-2 text-center" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
        <Package className="h-8 w-8" style={{ color: 'var(--text-tertiary)' }} />
        <p className="text-sm font-medium" style={{ color: 'var(--text-tertiary)' }}>Ürün verisi bulunamadı</p>
      </div>
    );
  }

  const allRows = otherRow ? [...rows, otherRow] : rows;

  return (
    <div className="rounded-2xl border p-5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      {/* Başlık */}
      <div className="flex items-start justify-between mb-5">
        <div>
          <h3 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            Ürün Bazında Bilet Dağılımı
          </h3>
          <p className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
            Toplam {totalTickets.toLocaleString('tr-TR')} bilet · İlk {Math.min(TOP_N, rows.length)} ürün
          </p>
        </div>
        <span
          className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold"
          style={{ backgroundColor: 'rgba(59,130,246,0.10)', color: '#1d4ed8' }}
        >
          {(data?.productMetrics ?? []).length} ürün
        </span>
      </div>

      {/* Satırlar */}
      <div className="space-y-4">
        {allRows.map((product, idx) => {
          const isOther = product.isOther;
          const color = PRODUCT_COLORS[isOther ? PRODUCT_COLORS.length - 1 : idx] ?? PRODUCT_COLORS[0];
          const pct = maxTotal > 0 ? ((product.totalTickets ?? 0) / maxTotal) * 100 : 0;
          const openPct = product.totalTickets > 0
            ? Math.round(((product.openTickets ?? 0) / product.totalTickets) * 100)
            : 0;

          return (
            <div
              key={product.productName}
              className="group relative"
              onMouseEnter={() => !isOther && setHoveredIdx(idx)}
              onMouseLeave={() => setHoveredIdx(null)}
            >
              {/* İsim + sayı */}
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
                    {openPct}% açık
                  </span>
                  <span
                    className="text-xs font-bold tabular-nums"
                    style={{ color: color.text }}
                  >
                    {(product.totalTickets ?? 0).toLocaleString('tr-TR')}
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
                      <span>Toplam Bilet</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {(product.totalTickets ?? 0).toLocaleString('tr-TR')}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>Açık Bilet</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {(product.openTickets ?? 0).toLocaleString('tr-TR')}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>Ort. Çözüm</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {product.avgResolutionHours != null ? `${fmt1(product.avgResolutionHours)}h` : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>CSAT Ort.</span>
                      <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {product.csatAverage != null && product.csatAverage > 0 ? `${fmt1(product.csatAverage)} / 5` : '—'}
                      </span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span>SLA Breach</span>
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

      {/* Renk lejantı */}
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
            Diğer
          </span>
        )}
      </div>
    </div>
  );
}

export default memo(ProductMetricsChart);
