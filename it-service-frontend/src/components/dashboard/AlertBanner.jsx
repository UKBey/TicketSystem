import { useState } from 'react';
import { AlertTriangle, ChevronDown, ChevronUp, Clock, Inbox, Users } from 'lucide-react';

const PRIORITY_COLOR = {
  CRITICAL: '#ef4444',
  HIGH:     '#f97316',
  MEDIUM:   '#f59e0b',
  LOW:      '#84cc16',
};

function formatHours(hours) {
  if (!hours && hours !== 0) return '—';
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}

function PriorityBadge({ priority }) {
  const color = PRIORITY_COLOR[priority] ?? '#94a3b8';
  return (
    <span
      className="inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide"
      style={{ backgroundColor: `${color}18`, color, border: `1px solid ${color}40` }}
    >
      {priority}
    </span>
  );
}

function BreachedItem({ item }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border px-3 py-2.5" style={{ backgroundColor: 'rgba(239,68,68,0.04)', borderColor: 'rgba(239,68,68,0.18)' }}>
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" style={{ color: '#ef4444' }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          Customer: {item.customerId}
          {item.hoursUntilDeadline !== null && item.hoursUntilDeadline !== undefined && (
            <span className="ml-2 font-semibold" style={{ color: '#ef4444' }}>
              {item.hoursUntilDeadline < 0
                ? `breached ${formatHours(Math.abs(item.hoursUntilDeadline))} ago`
                : `${formatHours(item.hoursUntilDeadline)} remaining`}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function UpcomingItem({ item }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border px-3 py-2.5" style={{ backgroundColor: 'rgba(245,158,11,0.04)', borderColor: 'rgba(245,158,11,0.18)' }}>
      <Clock className="mt-0.5 h-4 w-4 shrink-0" style={{ color: '#f59e0b' }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          Customer: {item.customerId}
          {item.hoursUntilDeadline !== null && item.hoursUntilDeadline !== undefined && (
            <span className="ml-2 font-semibold" style={{ color: '#f59e0b' }}>
              SLA breach in {formatHours(item.hoursUntilDeadline)}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function WaitingItem({ item }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border px-3 py-2.5" style={{ backgroundColor: 'rgba(148,163,184,0.06)', borderColor: 'rgba(148,163,184,0.2)' }}>
      <Clock className="mt-0.5 h-4 w-4 shrink-0" style={{ color: '#94a3b8' }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          Customer: {item.customerId}
          {item.hoursWaiting !== null && item.hoursWaiting !== undefined && (
            <span className="ml-2">waiting {formatHours(item.hoursWaiting)}</span>
          )}
        </div>
      </div>
    </div>
  );
}

export default function AlertBanner({ data, loading }) {
  const [expanded, setExpanded] = useState(true);

  const breached  = data?.breachedSLA     ?? [];
  const upcoming  = data?.upcomingBreach  ?? [];
  const waiting   = data?.waitingTooLong  ?? [];
  const backlog   = data?.backlogMetrics  ?? null;

  const totalAlerts = breached.length + upcoming.length + waiting.length;
  const hasContent  = totalAlerts > 0 || backlog;

  if (!loading && !hasContent) return null;

  return (
    <section
      className="rounded-3xl border shadow-sm"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
    >
      {/* Header */}
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="alert-banner-header flex w-full items-center gap-3 rounded-3xl px-5 py-4 text-left transition-colors"
        aria-expanded={expanded}
      >
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full" style={{ backgroundColor: 'rgba(239,68,68,0.1)' }}>
          <AlertTriangle className="h-4 w-4" style={{ color: '#ef4444' }} />
        </span>
        <div className="flex-1">
          <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
            Alerts &amp; Backlog
          </span>
          {!loading && totalAlerts > 0 && (
            <span className="ml-2 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-bold" style={{ backgroundColor: 'rgba(239,68,68,0.1)', color: '#ef4444' }}>
              {totalAlerts} alerts
            </span>
          )}
        </div>
        {expanded ? (
          <ChevronUp className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
        ) : (
          <ChevronDown className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
        )}
      </button>

      {expanded && (
        <div className="px-5 pb-5 space-y-5">

          {/* SLA Breach */}
          {(loading || breached.length > 0) && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: '#ef4444' }}>
                SLA Breach ({loading ? '…' : breached.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1, 2].map((i) => (
                    <div key={i} className="h-12 animate-pulse rounded-xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {breached.map((item) => <BreachedItem key={item.ticketId} item={item} />)}
                </div>
              )}
            </div>
          )}

          {/* Upcoming SLA */}
          {(loading || upcoming.length > 0) && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: '#f59e0b' }}>
                Upcoming SLA Breach ({loading ? '…' : upcoming.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1, 2].map((i) => (
                    <div key={i} className="h-12 animate-pulse rounded-xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {upcoming.map((item) => <UpcomingItem key={item.ticketId} item={item} />)}
                </div>
              )}
            </div>
          )}

          {/* Waiting Too Long */}
          {(loading || waiting.length > 0) && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: 'var(--text-secondary)' }}>
                Waiting Too Long ({loading ? '…' : waiting.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1].map((i) => (
                    <div key={i} className="h-12 animate-pulse rounded-xl" style={{ backgroundColor: 'var(--bg-surface-secondary)' }} />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {waiting.map((item) => <WaitingItem key={item.ticketId} item={item} />)}
                </div>
              )}
            </div>
          )}

          {/* Backlog Metrics */}
          <div className="grid grid-cols-3 gap-3">
            {[
              { icon: Users,  label: 'Unassigned',    value: loading ? null : (backlog?.unassignedCount ?? 0),    color: '#ef4444' },
              { icon: Inbox,  label: 'New Waiting',    value: loading ? null : (backlog?.newTicketsWaiting ?? 0), color: '#f59e0b' },
              { icon: Clock,  label: 'Avg. Wait',      value: loading ? null : formatHours(backlog?.avgWaitingHours), color: '#3b82f6' },
            ].map(({ icon: Icon, label, value, color }) => (
              <div key={label} className="rounded-2xl border px-3 py-3 text-center" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
                <Icon className="mx-auto mb-1 h-4 w-4" style={{ color }} />
                <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
                {value === null ? (
                  <div className="mx-auto mt-1 h-5 w-10 animate-pulse rounded" style={{ backgroundColor: 'var(--border-color-light)' }} />
                ) : (
                  <div className="mt-0.5 text-lg font-black" style={{ color: 'var(--text-primary)' }}>{value}</div>
                )}
              </div>
            ))}
          </div>

        </div>
      )}
    </section>
  );
}
