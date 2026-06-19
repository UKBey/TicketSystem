import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import { Award, Clock3, Flame, Star, Users } from 'lucide-react';
import Skeleton from '../Skeleton';

// Süpervizör (lead/admin) rozeti — LEAD_AGENT ve ADMIN rolleri.
function isLeadOrAdmin(role) {
  return role === 'LEAD_AGENT' || role === 'ADMIN';
}

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value ?? 0);
}

function formatHours(value, unit) {
  if (value === null || value === undefined) {
    return `0.0${unit}`;
  }

  return `${Number(value).toFixed(1)}${unit}`;
}

function formatMinutes(value, unit) {
  if (value === null || value === undefined) {
    return `0 ${unit}`;
  }

  return `${formatNumber(value)} ${unit}`;
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

function AgentPerformanceTable({ data, loading, onAgentClick }) {
  const { t } = useTranslation();
  const hourUnit = t('dashboard.units.hour');
  const minuteUnit = t('dashboard.units.minute');
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
            {t('dashboard.agentTable.badge')}
          </div>
          <h2 className="mt-3 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('dashboard.agentTable.title')}</h2>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('dashboard.agentTable.subtitle')}
          </p>
        </div>

        <div className="grid w-full grid-cols-2 gap-2 text-left text-xs sm:w-auto sm:grid-cols-2 lg:text-left">
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.statAgents')}</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalAgents)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.statActive')}</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalActiveTickets)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.statResolved24h')}</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{formatNumber(totalResolvedLast24Hours)}</div>
          </div>
          <div className="rounded-2xl px-3 py-2" style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <div className="text-[11px] uppercase tracking-[0.18em]" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.statAvgCsat')}</div>
            <div className="text-lg font-black" style={{ color: 'var(--text-primary)' }}>{Number(averageCsat).toFixed(1)}</div>
          </div>
        </div>
      </div>

      <div className="space-y-3 lg:hidden">
        {displayedAgents.map((agent, index) => {
          const workload = loading ? 45 : Math.min(100, Math.round(((agent.activeTickets ?? 0) / Math.max(totalActiveTickets, 1)) * 100));
          const csatTone = loading ? '' : getCsatTone(agent.csatAverage ?? 0);
          const barColor = getBarColor(index);

          const clickable = !loading && onAgentClick && agent.agentId;
          return (
            <div
              key={agent.agentId ?? agent.id ?? index}
              className={`rounded-xl border p-4 ${clickable ? 'cursor-pointer transition-colors hover:border-primary-400' : ''}`}
              style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
              onClick={clickable ? () => onAgentClick(agent) : undefined}
              role={clickable ? 'button' : undefined}
              tabIndex={clickable ? 0 : undefined}
              onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onAgentClick(agent); } } : undefined}
            >
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
                        {t('dashboard.agentTable.lead')}
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
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.colActive')}</div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-12" /> : formatNumber(agent.activeTickets)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.colResolved')}</div>
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-12" /> : formatNumber(agent.resolvedLast24Hours)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.colAvgResolution')}</div>
                  <div className="flex items-center gap-1.5 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                    <Clock3 className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : formatHours(agent.avgResolutionHours, hourUnit)}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.colCsat')}</div>
                  <div className={`flex items-center gap-1.5 text-sm font-bold ${csatTone}`}>
                    <Star className="h-4 w-4" />
                    {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : Number(agent.csatAverage ?? 0).toFixed(1)}
                  </div>
                </div>
                <div className="col-span-2">
                  <div className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('dashboard.agentTable.colSlaWorklog')}</div>
                  <div className="flex items-center gap-1.5 text-sm" style={{ color: 'var(--text-secondary)' }}>
                    <Flame className="h-4 w-4" style={{ color: 'var(--color-danger-500)' }} />
                    {loading ? (
                      <Skeleton as="span" className="inline-block h-4 w-20" />
                    ) : (
                      <span>
                        {t('dashboard.agentTable.slaCount', { count: formatNumber(agent.slaBreachedCount) })} <span aria-hidden="true">•</span> {formatMinutes(agent.worklogMinutesLast7Days, minuteUnit)}
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
        <div className="max-h-[30rem] overflow-y-auto">
        <div className="sticky top-0 z-10 grid grid-cols-[72px_minmax(180px,1.5fr)_110px_110px_120px_100px_120px] gap-0 border-b px-4 py-3 text-[11px] font-semibold uppercase tracking-[0.18em]" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)', color: 'var(--text-tertiary)' }}>
          <div>{t('dashboard.agentTable.colRank')}</div>
          <div>{t('dashboard.agentTable.colAgent')}</div>
          <div className="text-right">{t('dashboard.agentTable.colActive')}</div>
          <div className="text-right">{t('dashboard.agentTable.colResolved')}</div>
          <div className="text-right">{t('dashboard.agentTable.colAvgResolution')}</div>
          <div className="text-right">{t('dashboard.agentTable.colCsat')}</div>
          <div className="text-right">{t('dashboard.agentTable.colSlaWorklog')}</div>
        </div>

        <div className="divide-y" style={{ borderColor: 'var(--border-color-light)' }}>
          {displayedAgents.map((agent, index) => {
            const workload = loading ? 45 : Math.min(100, Math.round(((agent.activeTickets ?? 0) / Math.max(totalActiveTickets, 1)) * 100));
            const csatTone = loading ? '' : getCsatTone(agent.csatAverage ?? 0);
            const barColor = getBarColor(index);

            const clickable = !loading && onAgentClick && agent.agentId;
            return (
              <div
                key={agent.agentId ?? agent.id ?? index}
                className={`grid grid-cols-[72px_minmax(180px,1.5fr)_110px_110px_120px_100px_120px] items-center gap-0 px-4 py-4 transition-colors hover:bg-[color:var(--bg-surface-hover)] ${clickable ? 'cursor-pointer' : ''}`}
                onClick={clickable ? () => onAgentClick(agent) : undefined}
                role={clickable ? 'button' : undefined}
                tabIndex={clickable ? 0 : undefined}
                onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onAgentClick(agent); } } : undefined}
              >
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
                        {t('dashboard.agentTable.lead')}
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
                  {loading ? <Skeleton as="span" className="inline-block h-4 w-14" /> : formatHours(agent.avgResolutionHours, hourUnit)}
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
                      {t('dashboard.agentTable.slaCount', { count: formatNumber(agent.slaBreachedCount) })} <span aria-hidden="true">•</span> {formatMinutes(agent.worklogMinutesLast7Days, minuteUnit)}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
        </div>
      </div>

      {!loading && agents.length === 0 && (
        <div className="rounded-2xl border border-dashed px-4 py-10 text-center text-sm" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}>
          {t('dashboard.agentTable.empty')}
        </div>
      )}
    </section>
  );
}

export default memo(AgentPerformanceTable);