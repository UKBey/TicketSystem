import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Inbox } from 'lucide-react';
import { StatusBadge, PriorityBadge } from '../Badges';
import Skeleton from '../Skeleton';

/**
 * Compact, clickable "recent tickets" list for the personal dashboards. Reuses the
 * shared StatusBadge / PriorityBadge so colors match the rest of the app. Rows link
 * to the ticket detail page.
 */
export default function RecentTicketsList({ tickets, loading, title, emptyText }) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const formatDate = (value) => {
    if (!value) return '—';
    return new Date(value).toLocaleDateString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    });
  };

  return (
    <section className="rounded-2xl border p-4 shadow-sm sm:rounded-3xl sm:p-6" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
      <h2 className="mb-4 text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h2>

      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-12 w-full rounded-xl" />
        </div>
      ) : !tickets || tickets.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-10" style={{ color: 'var(--text-tertiary)' }}>
          <Inbox className="h-10 w-10 mb-3 opacity-30" />
          <p className="text-sm">{emptyText ?? t('ticket.empty.title')}</p>
        </div>
      ) : (
        <ul className="space-y-2">
          {tickets.map((ticket) => (
            <li
              key={ticket.id}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border px-3 py-2.5 cursor-pointer transition-colors"
              style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color-light)' }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-secondary)')}
            >
              <span className="text-xs font-semibold text-primary-500 shrink-0">TCK-{String(ticket.id).padStart(3, '0')}</span>
              <span className="flex-1 min-w-0 truncate text-sm font-medium" style={{ color: 'var(--text-primary)' }} title={ticket.title}>
                {ticket.title}
              </span>
              <StatusBadge status={ticket.status} />
              <PriorityBadge priority={ticket.priority} />
              <span className="text-[11px] shrink-0" style={{ color: 'var(--text-tertiary)' }}>{formatDate(ticket.createdAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
