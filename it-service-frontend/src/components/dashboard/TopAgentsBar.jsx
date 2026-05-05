import { Users } from 'lucide-react';
import { PRODUCT_COLORS } from './ChartColors';

export default function TopAgentsBar({ data, loading }) {
  const agentWorklogs = data?.agentWorklogs ?? [];
  const top5          = agentWorklogs.slice(0, 5);
  const maxMinutes    = top5.length > 0 ? top5[0].totalMinutes : 1;

  return (
    <section className="rounded-3xl border p-6 shadow-sm" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <div className="mb-4 flex items-center gap-2">
        <Users className="h-4 w-4" style={{ color: 'var(--text-secondary)' }} />
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]"
          style={{ color: 'var(--text-secondary)' }}>
          En Aktif Agentlar
        </h2>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex items-center gap-3">
              <div className="h-4 w-20 animate-pulse rounded"
                style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
              <div className="h-3 flex-1 animate-pulse rounded-full"
                style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
              <div className="h-4 w-12 animate-pulse rounded"
                style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
            </div>
          ))}
        </div>
      ) : top5.length === 0 ? (
        <p className="py-4 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
          Bu dönem kayıtlı worklog bulunamadı.
        </p>
      ) : (
        <div className="space-y-3">
          {top5.map((agent, idx) => {
            const c      = PRODUCT_COLORS[idx % PRODUCT_COLORS.length];
            const barPct = (agent.totalMinutes / maxMinutes) * 100;
            const hours  = (agent.totalMinutes / 60).toFixed(1);
            return (
              <div key={agent.agentId} className="flex items-center gap-3">
                <div className="w-24 shrink-0 truncate text-sm font-medium"
                  style={{ color: 'var(--text-primary)' }}
                  title={agent.agentUsername}>
                  {agent.agentUsername}
                </div>
                <div className="relative flex-1 overflow-hidden rounded-full"
                  style={{ height: '8px', backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <div className="top-agents-bar absolute inset-y-0 left-0 rounded-full"
                    style={{ width: `${barPct}%`, backgroundColor: c.bar }} />
                </div>
                <div className="w-16 shrink-0 text-right">
                  <span className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
                    {hours}sa
                  </span>
                  <span className="ml-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                    ({agent.totalEntries})
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}

    </section>
  );
}
