import { useTranslation } from 'react-i18next';
import { PriorityBadge } from '../Badges';
import { AlertTriangle, Pencil } from 'lucide-react';
import { formatDate, formatSlaTime } from '../../utils/ticketFormatters';

function DetailRow({ label, value }) {
  return (
    <div>
      <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      <div className="text-sm font-medium break-words" style={{ color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}

export default function TicketDetailsCard({
  ticket, slaInfo, currentDate,
  isCustomer, isAgent, isDark,
  onOpenPriorityModal, onOpenTopicModal,
}) {
  const { t } = useTranslation();

  const statusLabel = (status) => {
    const map = {
      NEW: t('ticketDetail.statusNew'),
      IN_PROGRESS: t('ticketDetail.statusInProgress'),
      WAITING_FOR_CUSTOMER: t('ticketDetail.statusWaiting'),
      RESOLVED: t('ticketDetail.statusResolved'),
      CLOSED: t('ticketDetail.statusClosed'),
    };
    return map[status] || status;
  };

  const renderSla = () => {
    if (!slaInfo) return null;
    const { slaState, remainingMs, fetchTime } = slaInfo;

    if (slaState === 'completed') return (
      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>
        {t('ticketDetail.slaCompleted')}
      </span>
    );
    if (slaState === 'expired') return (
      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}>
        <AlertTriangle className="h-3 w-3 mr-1" />{t('ticketDetail.slaExpired')}
      </span>
    );
    if (slaState === 'paused') return (
      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" style={{ backgroundColor: isDark ? 'rgba(100,116,139,0.3)' : '#f1f5f9', color: isDark ? '#cbd5e1' : '#475569' }}>
        {formatSlaTime(remainingMs)} ({t('ticketDetail.slaPaused')})
      </span>
    );

    const diff = remainingMs - (currentDate - fetchTime);
    if (diff <= 0) return (
      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold animate-pulse-subtle" style={{ backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }}>
        <AlertTriangle className="h-3 w-3 mr-1" />{t('ticketDetail.slaExpired')}
      </span>
    );
    const totalMins = Math.floor(diff / 60000);
    let badgeStyle = { backgroundColor: isDark ? 'rgba(34,197,94,0.2)' : '#dcfce7', color: isDark ? '#86efac' : '#166534' };
    let extraCls = '';
    if (totalMins < 1) { badgeStyle = { backgroundColor: isDark ? 'rgba(239,68,68,0.2)' : '#fee2e2', color: isDark ? '#fca5a5' : '#991b1b' }; extraCls = 'animate-pulse-subtle font-bold'; }
    else if (totalMins < 2) { badgeStyle = { backgroundColor: isDark ? 'rgba(245,158,11,0.2)' : '#fef3c7', color: isDark ? '#fde68a' : '#92400e' }; }
    return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${extraCls}`} style={badgeStyle}>{formatSlaTime(diff)}</span>;
  };

  return (
    <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
        {t('ticketDetail.ticketDetails')}
      </div>
      <div className="p-4 sm:p-5 space-y-4">
        <DetailRow label={t('ticketDetail.created')} value={formatDate(ticket.createdAt)} />

        {!isCustomer && (
          <div>
            <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.claimers')}</div>
            {ticket.claimers && ticket.claimers.length > 0 ? (
              <div className="flex flex-wrap gap-1">
                {ticket.claimers.map((c) => (
                  <span key={c.agentId} className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}>
                    {c.agentName}
                  </span>
                ))}
              </div>
            ) : (
              <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.unassigned')}</span>
            )}
          </div>
        )}

        <DetailRow label={t('ticketDetail.statusLabel')} value={statusLabel(ticket.status)} />

        <div>
          <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.priority')}</div>
          <div className="flex flex-wrap items-center gap-2">
            <PriorityBadge priority={ticket.priority} />
            {isAgent && ticket.status !== 'CLOSED' && (
              <button
                onClick={onOpenPriorityModal}
                className="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-medium transition-colors cursor-pointer hover:bg-[var(--bg-surface-secondary)]"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                title={t('ticketDetail.changePriority')}
              >
                <Pencil className="h-3 w-3" />
                {t('ticketDetail.changePriority')}
              </button>
            )}
          </div>
        </div>

        {(ticket.topicName || ticket.topicId) && (
          <div>
            <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.topicLabel')}</div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium break-words" style={{ color: 'var(--text-primary)' }}>
                {ticket.topicName || `#${ticket.topicId}`}
              </span>
              {isAgent && ticket.status !== 'CLOSED' && (
                <button
                  onClick={onOpenTopicModal}
                  className="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-medium transition-colors cursor-pointer hover:bg-[var(--bg-surface-secondary)]"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                  title={t('ticketDetail.changeTopic')}
                >
                  <Pencil className="h-3 w-3" />
                  {t('ticketDetail.changeTopic')}
                </button>
              )}
            </div>
          </div>
        )}

        {slaInfo && (
          <div>
            <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.slaRemaining')}</div>
            {renderSla()}
          </div>
        )}

        {ticket.closedAt && <DetailRow label={t('ticketDetail.closedAt')} value={formatDate(ticket.closedAt)} />}

      </div>
    </div>
  );
}
