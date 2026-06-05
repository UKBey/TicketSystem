import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, ChevronDown, ChevronUp, Clock, Inbox, Users } from 'lucide-react';
import Skeleton from '../Skeleton';
import { PRIORITY_COLORS } from '../../constants/ticketColors';

// Tek bir alert satırını ticket detayına tıklanabilir/klavyeyle erişilebilir yapan ortak prop'lar.
function rowOpenProps(onOpen) {
  return {
    onClick: onOpen,
    role: 'button',
    tabIndex: 0,
    onKeyDown: (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(); }
    },
  };
}

const ROW_INTERACTIVE = 'cursor-pointer transition-shadow hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring-color)]';

const PRIORITY_COLOR = {
  CRITICAL: PRIORITY_COLORS.CRITICAL.solid,
  HIGH:     PRIORITY_COLORS.HIGH.solid,
  MEDIUM:   PRIORITY_COLORS.MEDIUM.solid,
  LOW:      PRIORITY_COLORS.LOW.solid,
};

function formatDuration(hours) {
  if (hours === null || hours === undefined || Number.isNaN(hours)) return '—';
  const totalSeconds = Math.round(Math.abs(hours) * 3600);

  if (totalSeconds < 60) return `${totalSeconds}s`;
  if (totalSeconds < 3600) {
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return s > 0 ? `${m}m ${s}s` : `${m}m`;
  }
  if (totalSeconds < 86_400) {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    return m > 0 ? `${h}h ${m}m` : `${h}h`;
  }
  const d = Math.floor(totalSeconds / 86_400);
  const h = Math.floor((totalSeconds % 86_400) / 3600);
  return h > 0 ? `${d}d ${h}h` : `${d}d`;
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

function BreachedItem({ item, t, onOpen }) {
  return (
    <div {...rowOpenProps(onOpen)} className={`flex items-start gap-3 rounded-xl border px-3 py-2.5 ${ROW_INTERACTIVE}`} style={{ backgroundColor: 'rgba(239,68,68,0.04)', borderColor: 'rgba(239,68,68,0.18)' }}>
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" style={{ color: '#ef4444' }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          {item.customerName ?? item.customerId}
          {item.hoursUntilDeadline !== null && item.hoursUntilDeadline !== undefined && (
            <span className="ml-2 font-semibold" style={{ color: '#ef4444' }}>
              {item.hoursUntilDeadline < 0
                ? t('dashboard.alerts.breachedAgo', { time: formatDuration(Math.abs(item.hoursUntilDeadline)) })
                : t('dashboard.alerts.remaining',   { time: formatDuration(item.hoursUntilDeadline) })}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function UpcomingItem({ item, t, onOpen }) {
  return (
    <div {...rowOpenProps(onOpen)} className={`flex items-start gap-3 rounded-xl border px-3 py-2.5 ${ROW_INTERACTIVE}`} style={{ backgroundColor: 'rgba(245,158,11,0.04)', borderColor: 'rgba(245,158,11,0.18)' }}>
      <Clock className="mt-0.5 h-4 w-4 shrink-0" style={{ color: '#f59e0b' }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          {item.customerName ?? item.customerId}
          {item.hoursUntilDeadline !== null && item.hoursUntilDeadline !== undefined && (
            <span className="ml-2 font-semibold" style={{ color: '#f59e0b' }}>
              {t('dashboard.alerts.breachIn', { time: formatDuration(item.hoursUntilDeadline) })}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

// Bekleme satırındaki durum etiketinin (WAITING_FOR_CUSTOMER / RESOLVED) rengi.
function statusTagStyle(status) {
  const c = status === 'RESOLVED' ? '#14b8a6' : '#94a3b8';
  return { backgroundColor: `${c}18`, color: c, border: `1px solid ${c}40` };
}

function WaitingItem({ item, t, onOpen }) {
  const tagColor = item.status === 'RESOLVED' ? '#14b8a6' : '#94a3b8';
  return (
    <div {...rowOpenProps(onOpen)} className={`flex items-start gap-3 rounded-xl border px-3 py-2.5 ${ROW_INTERACTIVE}`} style={{ backgroundColor: 'rgba(148,163,184,0.06)', borderColor: 'rgba(148,163,184,0.2)' }}>
      <Clock className="mt-0.5 h-4 w-4 shrink-0" style={{ color: tagColor }} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>#{item.ticketId} {item.title}</span>
          <PriorityBadge priority={item.priority} />
          {item.status && (
            <span className="inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide" style={statusTagStyle(item.status)}>
              {t(`ticket.status.${item.status.toLowerCase()}`)}
            </span>
          )}
        </div>
        <div className="mt-0.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
          {item.customerName ?? item.customerId}
          {item.hoursWaiting !== null && item.hoursWaiting !== undefined && (
            <span className="ml-2">{t('dashboard.alerts.waitingFor', { time: formatDuration(item.hoursWaiting) })}</span>
          )}
        </div>
      </div>
    </div>
  );
}

export default function AlertBanner({ data, loading }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // İlk girişte kapalı başlar — kullanıcı başlığa tıklayarak açar.
  const [expanded, setExpanded] = useState(false);

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
            {t('dashboard.alerts.title')}
          </span>
          {!loading && totalAlerts > 0 && (
            <span className="ml-2 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-bold" style={{ backgroundColor: 'rgba(239,68,68,0.1)', color: '#ef4444' }}>
              {t('dashboard.alerts.count', { count: totalAlerts })}
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
                {t('dashboard.alerts.slaBreach')} ({loading ? '…' : breached.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1, 2].map((i) => (
                    <Skeleton key={i} className="h-12 rounded-xl" />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {breached.map((item) => <BreachedItem key={item.ticketId} item={item} t={t} onOpen={() => navigate(`/tickets/${item.ticketId}`)} />)}
                </div>
              )}
            </div>
          )}

          {/* Upcoming SLA */}
          {(loading || upcoming.length > 0) && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: '#f59e0b' }}>
                {t('dashboard.alerts.upcomingBreach')} ({loading ? '…' : upcoming.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1, 2].map((i) => (
                    <Skeleton key={i} className="h-12 rounded-xl" />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {upcoming.map((item) => <UpcomingItem key={item.ticketId} item={item} t={t} onOpen={() => navigate(`/tickets/${item.ticketId}`)} />)}
                </div>
              )}
            </div>
          )}

          {/* Waiting Too Long */}
          {(loading || waiting.length > 0) && (
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.18em]" style={{ color: 'var(--text-secondary)' }}>
                {t('dashboard.alerts.waitingTooLong')} ({loading ? '…' : waiting.length})
              </p>
              {loading ? (
                <div className="space-y-2">
                  {[1].map((i) => (
                    <Skeleton key={i} className="h-12 rounded-xl" />
                  ))}
                </div>
              ) : (
                <div className="space-y-2">
                  {waiting.map((item) => <WaitingItem key={item.ticketId} item={item} t={t} onOpen={() => navigate(`/tickets/${item.ticketId}`)} />)}
                </div>
              )}
            </div>
          )}

          {/* Backlog Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {[
              { icon: Users,  label: t('dashboard.alerts.unassigned'), value: loading ? null : (backlog?.unassignedCount ?? 0),    color: '#ef4444' },
              { icon: Inbox,  label: t('dashboard.alerts.newWaiting'), value: loading ? null : (backlog?.newTicketsWaiting ?? 0), color: '#f59e0b' },
              { icon: Clock,  label: t('dashboard.alerts.avgWait'),    value: loading ? null : formatDuration(backlog?.avgWaitingHours), color: '#3b82f6' },
            ].map(({ icon: Icon, label, value, color }) => (
              <div key={label} className="rounded-2xl border px-3 py-3 text-center" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}>
                <Icon className="mx-auto mb-1 h-4 w-4" style={{ color }} />
                <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
                {value === null ? (
                  <Skeleton className="mx-auto mt-1 h-5 w-10" style={{ backgroundColor: 'var(--border-color-light)' }} />
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
