import { createElement } from 'react';

export default function KpiCard({ title, value, detail, icon, accent, loading }) {
  return (
    <article className="group rounded-3xl border p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em]" style={{ color: 'var(--text-tertiary)' }}>{title}</p>
          <div className="mt-3 text-3xl font-black tracking-tight" style={{ color: 'var(--text-primary)' }}>
            {loading ? <span className="inline-block h-9 w-28 animate-pulse rounded-lg" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} /> : value}
          </div>
        </div>

        <div className={`flex h-12 w-12 items-center justify-center rounded-2xl ${accent} text-white shadow-md`}>
          {icon ? createElement(icon, { className: 'h-5 w-5' }) : null}
        </div>
      </div>

      <div className="mt-4 flex items-center gap-2 text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>
        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: 'var(--text-tertiary)' }} />
        {loading ? 'Loading data' : detail}
      </div>
    </article>
  );
}