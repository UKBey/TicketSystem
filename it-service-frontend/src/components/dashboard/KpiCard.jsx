import { createElement } from 'react';
import Skeleton from '../Skeleton';
import AnimatedNumber from '../AnimatedNumber';

export default function KpiCard({ title, value, detail, icon, accent, loading }) {
  // Yukleme placeholder'i da bu kartin sekliyle render edilir (ayni article/padding),
  // boylece skeleton -> icerik gecisinde layout "ziplamaz"; ic alanlar shimmer'lar,
  // icerik geldiginde fade-in ile yumusak belirir.
  const accentClass = accent || 'bg-gray-200 dark:bg-gray-700';
  return (
    <article className="group rounded-2xl border p-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg sm:rounded-3xl sm:p-5" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="flex items-start justify-between gap-3 sm:gap-4">
        <div className="min-w-0 flex-1">
          {loading
            ? <Skeleton className="h-2.5 w-24 rounded sm:h-3" />
            : <p className="text-[10px] font-semibold uppercase tracking-[0.18em] animate-fade-in sm:text-xs sm:tracking-[0.2em]" style={{ color: 'var(--text-tertiary)' }}>{title}</p>}
          <div className="mt-2 text-2xl font-black tracking-tight sm:mt-3 sm:text-3xl" style={{ color: 'var(--text-primary)' }}>
            {loading ? <Skeleton as="span" className="inline-block h-8 w-20 rounded-lg sm:h-9 sm:w-28" /> : <span className="inline-block animate-fade-in"><AnimatedNumber value={value} /></span>}
          </div>
        </div>

        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${loading ? `${accentClass} animate-pulse` : accentClass} text-white shadow-md sm:h-12 sm:w-12 sm:rounded-2xl`}>
          {!loading && icon ? createElement(icon, { className: 'h-4 w-4 sm:h-5 sm:w-5' }) : null}
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 text-xs font-medium sm:mt-4 sm:text-sm" style={{ color: 'var(--text-secondary)' }}>
        <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: 'var(--text-tertiary)' }} />
        {loading ? <Skeleton className="h-2.5 w-28 rounded" /> : <span className="truncate animate-fade-in">{detail}</span>}
      </div>
    </article>
  );
}