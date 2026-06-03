import { memo } from 'react';
import { Award, Clock3, Flame, Star, Users } from 'lucide-react';
import Skeleton from '../Skeleton';

// Süpervizör (lead/admin) rozeti — LEAD_AGENT ve ADMIN rolleri.
function isLeadOrAdmin(role) {
  return role === 'LEAD_AGENT' || role === 'ADMIN';
}

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
}

function formatHours(value) {
  if (value === null || value === undefined) {
    return '0.0h';
  }

  return `${Number(value).toFixed(1)}h`;
}

function formatMinutes(value) {
  if (value === null || value === undefined) {
    return '0 min';
  }

  return `${formatNumber(value)} min`;
}

function getRankBadge(index) {
  if (index === 0) return '🥇';
  if (index === 1) return '🥈';
  if (index === 2) return '🥉';
  return `#${index + 1}`;
}

function getCsatTone(value) {
  if (value >= 4.5) return 'text-accent-600 dark:text-accent-400';
  if (value >= 4.0) return 'text-warning-600 dark:text-warning-400';
  return 'text-danger-600 dark:text-danger-400';
}

function getBarColor(index) {
  if (index === 0) return 'bg-primary-500';
  if (index === 1) return 'bg-accent-500';
  if (index === 2) return 'bg-warning-500';
  return 'bg-slate-400';
}

function createPlaceholderRows() {
  return Array.from({ length: 5 }, (_, index) => ({ id: index }));
}

function AgentPerformanceTable({ data, loading }) {
  const agents = data?.agents ?? [];
  const totalAgents = data?.totalAgents ?? 0;
  const totalActiveTickets = data?.totalActiveTickets ?? 0;
  const totalResolvedLast24Hours = data?.totalResolvedLast24Hours ?? 0;
  const averageCsat = data?.averageCsat ?? 0;

  const displayedAgents = loading ? createPlaceholderRows() : agents;

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <div className="mb-5 flex flex-col items-start justify-between gap-4 lg:flex-row">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
            <Users className="h-3.5 w-3.5" />
            Agent leaderboard
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>Performance ranking</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            Active workload, resolutions in the last 24h, avg. resolution time and CSAT in one table.
          </p>
        </div>

        <div className="grid w-full grid-cols-2 gap-2 text-left text-xs sm:w-auto sm:grid-cols-2 lg:text-left">
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Agents</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalAgents)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Active</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalActiveTickets)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Resolved 24h</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalResolvedLast24Hours)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>Avg CSAT</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{Number(averageCsat).toFixed(1)}</div>
          </div>
        </div>
      </div>

      <div className="space-y-3 lg:hidden">
        {displayedAgents.map((agent, index) => {
          const workload = loading ? 45 : Math.min(100, Math.round(((agent.activeTickets ?? 0) / Math.max(totalActiveTickets, 1)) * 100));
          const csatTone = loading ? '' : getCsatTone(agent.csatAverage ?? 0);
          const barColor = getBarColor(index);

          return (
            <div key={agent.agentId ?? agent.id ?? index} className="rounded-xl border p-4" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center gap-3">
                <span className="inline-flex min-w-12 items-center justify-center rounded-full border px-2 py-1 text-xs font-black" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
                  {loading ? <Skeleton as="span" className="h-4 w-6" style={{ backgroundColor: 'var(--bg-surface)' }} /> : getRankBadge(index)}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {loading ? <Skeleton as="span" className="inline-block h-4 w-32" /> : agent.agentName}
                    </div>
                    {!loading && isLeadOrAdmin(agent.role) && (
                      <span className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em]" style={{ backgroundColor: 'rgba(59,130,246,0.08)', borderColor: 'rgba(59,130,246,0.18)', color: 'var(--color-primary-700)' }}>
                        <Award className="h-3 w-3" />
                        Lead
                      </span>
                    )}
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                    <div className={`h-full rounded-full ${barColor}`} style={{ width: `${Math.max(workload, 10)}%` }} />
                  </div>
                </div>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>Active</div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-12" /> : formatNumber(agent.activeTickets)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>Resolved</div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-12" /> : formatNumber(agent.resolvedLast24Hours)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>Avg. resolution</div>
                  <div className="flex items-center gap-1.5 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    <Clock3 className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : formatHours(agent.avgResolutionHours)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>CSAT</div>
                  <div className={`flex items-center gap-1.5 text-sm font-bold ${csatTone}`}>
                    <Star className="h-4 w-4" />
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : Number(agent.csatAverage ?? 0).toFixed(1)}
                  </div>
                </div>
                <div className="col-span-2">
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>SLA / Worklog</div>
                  <div className="flex items-center gap-1.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
                    <Flame className="h-4 w-4" style={{ color: 'var(--color-danger-500)' }} />
                    {loading ? (
                      <Skeleton as="span" className="inline-block h-4 w-20" />
                    ) : (
                      <span>
                        {formatNumber(agent.slaBreachedCount)} SLA <span aria-hidden="true">•</span> {formatMinutes(agent.worklogMinutesLast7Days)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="hidden overflow-hidden rounded-2xl border lg:block" style={{ borderColor: 'var(--border-color-light)' }}>
        <div className="grid grid-cols-[72px_minmax(180px,1.5fr)_110px_110px_120px_100px_120px] gap-0 border-b px-4 py-3 text-[11px] font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)', color: 'var(--text-tertiary)' }}>
          <div>Rank</div>
          <div>Agent</div>
          <div className="text-right">Active</div>
          <div className="text-right">Resolved</div>
          <div className="text-right">Avg. resolution</div>
          <div className="text-right">CSAT</div>
          <div className="text-right">SLA / Worklog</div>
        </div>

        <div className="divide-y" style={{ borderColor: 'var(--border-color-light)' }}>
          {displayedAgents.map((agent, index) => {
            const workload = loading ? 45 : Math.min(100, Math.round(((agent.activeTickets ?? 0) / Math.max(totalActiveTickets, 1)) * 100));
            const csatTone = loading ? '' : getCsatTone(agent.csatAverage ?? 0);
            const barColor = getBarColor(index);

            return (
              <div key={agent.agentId ?? agent.id ?? index} className="grid grid-cols-[72px_minmax(180px,1.5fr)_110px_110px_120px_100px_120px] items-center gap-0 px-4 py-4 transition-colors hover:bg-[color:var(--bg-surface-hover)]">
                <div className="flex items-center">
                  <span className="inline-flex min-w-12 items-center justify-center rounded-full border px-2 py-1 text-xs font-black" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
                    {loading ? <Skeleton as="span" className="h-4 w-6" style={{ backgroundColor: 'var(--bg-surface)' }} /> : getRankBadge(index)}
                  </span>
                </div>

                <div>
                  <div className="flex items-center gap-2">
                    <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {loading ? <Skeleton as="span" className="inline-block h-4 w-32" /> : agent.agentName}
                    </div>
                    {!loading && isLeadOrAdmin(agent.role) && (
                      <span className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em]" style={{ backgroundColor: 'rgba(59,130,246,0.08)', borderColor: 'rgba(59,130,246,0.18)', color: 'var(--color-primary-700)' }}>
                        <Award className="h-3 w-3" />
                        Lead
                      </span>
                    )}
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                    <div className={`h-full rounded-full ${barColor}`} style={{ width: `${Math.max(workload, 10)}%` }} />
                  </div>
                </div>

                <div className="text-right text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {loading ? <Skeleton as="span" className="ml-auto inline-block h-4 w-12" /> : formatNumber(agent.activeTickets)}
                </div>

                <div className="text-right text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  {loading ? <Skeleton as="span" className="ml-auto inline-block h-4 w-12" /> : formatNumber(agent.resolvedLast24Hours)}
                </div>

                <div className="flex items-center justify-end gap-2 text-right text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                  <Clock3 className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                  {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : formatHours(agent.avgResolutionHours)}
                </div>

                <div className={`flex items-center justify-end gap-2 text-right text-sm font-bold ${csatTone}`}>
                  <Star className="h-4 w-4" />
                  {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : Number(agent.csatAverage ?? 0).toFixed(1)}
                </div>

                <div className="flex items-center justify-end gap-2 text-right text-sm" style={{ color: 'var(--text-secondary)' }}>
                  <Flame className="h-4 w-4" style={{ color: 'var(--color-danger-500)' }} />
                  {loading ? (
                    <Skeleton as="span" className="inline-block h-4 w-20" />
                  ) : (
                    <span>
                      {formatNumber(agent.slaBreachedCount)} SLA <span aria-hidden="true">•</span> {formatMinutes(agent.worklogMinutesLast7Days)}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {!loading && agents.length === 0 && (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          No agent performance records to display.
        </div>
      )}
    </section>
  );
}

export default memo(AgentPerformanceTable);