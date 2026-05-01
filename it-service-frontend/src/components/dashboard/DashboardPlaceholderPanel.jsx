import { Activity, BarChart3, PieChart } from 'lucide-react';

const panels = {
  'Zaman çizgisi grafiği': Activity,
  'Agent workload görünümü': BarChart3,
  default: PieChart,
};

export default function DashboardPlaceholderPanel({ title, description }) {
  const Icon = panels[title] || panels.default;

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>{description}</p>
        </div>
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
          <Icon className="h-5 w-5" style={{ color: 'var(--text-tertiary)' }} />
        </div>
      </div>

      <div className="mt-5 rounded-3xl border border-dashed p-8" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}>
        <div className="mx-auto flex max-w-md flex-col items-center text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
            <Icon className="h-7 w-7" style={{ color: 'var(--text-tertiary)' }} />
          </div>
          <h3 className="mt-4 text-base font-bold" style={{ color: 'var(--text-primary)' }}>Chart alanı hazır</h3>
          <p className="mt-2 text-sm leading-6" style={{ color: 'var(--text-secondary)' }}>
            Bu bölüm sonraki commitlerde veri görselleştirmeleri için kullanılacak. Şimdilik layout'u sabit tutuyor ve dashboard'un boş görünmesini engelliyor.
          </p>
        </div>
      </div>
    </section>
  );
}