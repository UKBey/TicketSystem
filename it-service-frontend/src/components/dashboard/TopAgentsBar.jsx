import { memo } from 'react';
import { Users } from 'lucide-react';
import Skeleton from '../Skeleton';
import { PRODUCT_COLORS } from './ChartColors';

function TopAgentsBar({ data, loading }) {
  const agentWorklogs = data?.agentWorklogs ?? [];
  const top5          = agentWorklogs.slice(0, 5);
  const maxMinutes    = top5.length > 0 ? top5[0].totalMinutes : 1;

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>

      <div className="mb-4 flex items-center gap-2">
        <Users className="h-4 w-4" style={{ color: 'var(--text-secondary)' }} />
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]"
          style={{ color: 'var(--text-secondary)' }}>
          Most Active Agents
        </h2>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="h-4 w-20" />
              <Skeleton className="h-3 flex-1 rounded-full" />
              <Skeleton className="h-4 w-12" />
            </div>
          ))}
        </div>
      ) : top5.length === 0 ? (
        <p className="py-4 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
          No worklog entries found for this period.
        </p>
      ) : (
        <div className="space-y-3">
          {top5.map((agent, idx) => {
            const c      = PRODUCT_COLORS[idx % PRODUCT_COLORS.length];
            const barPct = (agent.totalMinutes / maxMinutes) * 100;
            const hours  = (agent.totalMinutes / 60).toFixed(1);
            return (
              <div key={agent.agentId} className="flex items-center gap-3">
                <div className="w-16 shrink-0 truncate text-xs font-medium sm:w-24 sm:text-sm"
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
                    {hours}h
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

export default memo(TopAgentsBar);
