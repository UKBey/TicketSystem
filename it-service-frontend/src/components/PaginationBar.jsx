import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/**
 * Reusable pagination bar.
 *
 * Props:
 *   page        {number}   0-based current page
 *   totalPages  {number}
 *   totalItems  {number}
 *   size        {number}   current page size
 *   onPageChange  (page: number) => void
 *   onSizeChange  (size: number) => void
 */
export default function PaginationBar({ page, totalPages, totalItems, size, onPageChange, onSizeChange }) {
  const { t } = useTranslation();

  if (totalPages <= 0) return null;

  const from = page * size + 1;
  const to   = Math.min((page + 1) * size, totalItems);

  const pages = buildPageNumbers(page, totalPages);

  return (
    <div className="flex flex-col sm:flex-row sm:flex-wrap sm:items-center sm:justify-between gap-3 px-4 py-3 border-t text-sm"
      style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>

      {/* Left: count + page size */}
      <div className="flex items-center gap-3 flex-wrap">
        <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
          {from}–{to} {t('common.of')} {totalItems}
        </span>
        <select
          value={size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          className="rounded-lg border px-2 py-1 text-xs outline-none cursor-pointer"
          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
        >
          {PAGE_SIZE_OPTIONS.map((s) => (
            <option key={s} value={s}>{t('common.perPage', { count: s })}</option>
          ))}
        </select>
      </div>

      {/* Right: page buttons */}
      <div className="flex items-center gap-1 flex-wrap">
        <PageBtn onClick={() => onPageChange(page - 1)} disabled={page === 0} aria-label={t('common.previousPage')}>
          <ChevronLeft className="h-3.5 w-3.5" />
        </PageBtn>

        {pages.map((p, i) =>
          p === '…' ? (
            <span key={`ellipsis-${i}`} className="px-2 text-xs" style={{ color: 'var(--text-tertiary)' }}>…</span>
          ) : (
            <PageBtn key={p} active={p === page} onClick={() => onPageChange(p)}>
              {p + 1}
            </PageBtn>
          )
        )}

        <PageBtn onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1} aria-label={t('common.nextPage')}>
          <ChevronRight className="h-3.5 w-3.5" />
        </PageBtn>
      </div>
    </div>
  );
}

function PageBtn({ children, active, disabled, onClick, ...rest }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex items-center justify-center min-w-[36px] h-9 sm:min-w-[28px] sm:h-7 rounded-md px-1.5 text-xs font-medium transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
      style={{
        backgroundColor: active ? '#3b82f6' : 'var(--bg-surface-secondary)',
        color:           active ? '#ffffff'  : 'var(--text-secondary)',
        border:          active ? 'none'     : '1px solid var(--border-color)',
      }}
      {...rest}
    >
      {children}
    </button>
  );
}

/** Build a compact page number array with ellipsis. */
function buildPageNumbers(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);

  const pages = new Set([0, total - 1, current]);
  for (let d = -2; d <= 2; d++) {
    const p = current + d;
    if (p >= 0 && p < total) pages.add(p);
  }

  const sorted = [...pages].sort((a, b) => a - b);
  const result = [];
  let prev = -1;
  for (const p of sorted) {
    if (p - prev > 1) result.push('…');
    result.push(p);
    prev = p;
  }
  return result;
}
