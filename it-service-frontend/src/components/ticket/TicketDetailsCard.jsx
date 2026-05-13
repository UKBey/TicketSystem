import { useTranslation } from 'react-i18next';
import { PriorityBadge } from '../Badges';
import { Clock, CheckCircle2, ChevronDown, ChevronUp, AlertTriangle } from 'lucide-react';
import { formatDate, formatShortDate, formatSlaTime, getAuditActionStyles } from '../../utils/ticketFormatters';

function DetailRow({ label, value }) {
  return (
    <div>
      <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}

export default function TicketDetailsCard({
  ticket, slaInfo, currentDate, resolutionNote,
  isCustomer, isDark,
  auditHistoryExpanded, setAuditHistoryExpanded,
}) {
  const { t } = useTranslation();

  const auditLogs = Array.isArray(ticket.auditLogs)
    ? ticket.auditLogs
    : Array.isArray(ticket.ticketAuditLogs)
      ? ticket.ticketAuditLogs
      : [];

  const getAuditActionLabel = (actionType) => {
    const labels = {
      UNCLAIM: t('ticketDetail.auditReleased'),
      CLOSE:   t('ticketDetail.auditClosed'),
      CLAIM:   t('ticketDetail.auditClaimed'),
    };
    return labels[actionType] || actionType || t('ticketDetail.auditUpdated');
  };

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
      <div className="p-5 space-y-4">
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
          <PriorityBadge priority={ticket.priority} />
        </div>

        {slaInfo && (
          <div>
            <div className="text-xs font-medium mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('ticketDetail.slaRemaining')}</div>
            {renderSla()}
          </div>
        )}

        {ticket.resolvedAt && <DetailRow label={t('ticketDetail.resolvedAt')} value={formatDate(ticket.resolvedAt)} />}
        {ticket.closedAt   && <DetailRow label={t('ticketDetail.closedAt')} value={formatDate(ticket.closedAt)} />}

        {resolutionNote && (
          <div>
            <div className="text-xs font-medium mb-1 flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
              <CheckCircle2 className="h-3 w-3 text-accent-500" />
              {t('ticketDetail.resolutionNote')}
            </div>
            <div className="text-xs leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
              {resolutionNote.note}
            </div>
            <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
              {resolutionNote.agentId} · {formatShortDate(resolutionNote.updatedAt || resolutionNote.createdAt)}
            </div>
          </div>
        )}

        {!isCustomer && (
          <div className="pt-2 border-t" style={{ borderColor: 'var(--border-color)' }}>
            <button
              className="w-full flex items-center justify-between mb-2 cursor-pointer group"
              onClick={() => setAuditHistoryExpanded((v) => !v)}
            >
              <div className="text-xs font-medium flex items-center gap-1" style={{ color: 'var(--text-tertiary)' }}>
                <Clock className="h-3 w-3" />
                {t('ticketDetail.auditHistory')}
                {auditLogs.length > 0 && (
                  <span className="ml-1 inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold" style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }}>
                    {auditLogs.length}
                  </span>
                )}
              </div>
              {auditHistoryExpanded
                ? <ChevronUp className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                : <ChevronDown className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--text-tertiary)' }} />}
            </button>
            {auditHistoryExpanded && (
              auditLogs.length > 0 ? (
                <div className="space-y-2">
                  {auditLogs.map((entry) => (
                    <div key={entry.id} className="rounded-lg border px-3 py-2.5" style={{ borderColor: 'var(--border-color-light)', backgroundColor: 'var(--bg-surface-secondary)' }}>
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold" style={getAuditActionStyles(entry.actionType, isDark)}>
                              {getAuditActionLabel(entry.actionType)}
                            </span>
                            <span className="text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
                              {entry.actorName || entry.actorId || 'Unknown actor'}
                            </span>
                          </div>
                          <div className="text-xs mt-1 leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                            {entry.note || t('ticketDetail.noNote')}
                          </div>
                        </div>
                        <div className="text-[11px] shrink-0 text-right" style={{ color: 'var(--text-tertiary)' }}>
                          {formatShortDate(entry.createdAt)}
                        </div>
                      </div>
                      <div className="mt-2 flex items-center gap-2 text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
                        <span>{entry.previousState || '—'}</span>
                        <span>→</span>
                        <span>{entry.newState || '—'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-lg border border-dashed px-3 py-3 text-xs leading-relaxed" style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}>
                  {t('ticketDetail.auditHistoryPlaceholder')}
                </div>
              )
            )}
          </div>
        )}
      </div>
    </div>
  );
}
