import { useTranslation } from 'react-i18next';
import { PriorityBadge } from '../Badges';
import { CheckCircle2, AlertTriangle } from 'lucide-react';
import { formatDate, formatShortDate, formatSlaTime } from '../../utils/ticketFormatters';

function DetailRow({ label, value }) {
  return (
    <div>
      <div className="text-xs font-medium mb-0.5" style={{ color: 'var(--text-tertiary)' }}>{label}</div>
      <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}

const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const PRIORITY_STYLES = {
  LOW:      { color: '#22c55e', bg: 'rgba(34,197,94,0.12)'   },
  MEDIUM:   { color: '#f59e0b', bg: 'rgba(245,158,11,0.12)'  },
  HIGH:     { color: '#ef4444', bg: 'rgba(239,68,68,0.12)'   },
  CRITICAL: { color: '#7c3aed', bg: 'rgba(124,58,237,0.12)'  },
};

export default function TicketDetailsCard({
  ticket, slaInfo, currentDate, resolutionNote,
  isCustomer, isAgent, isDark, onPriorityChange,
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
          {isAgent && ticket.status !== 'CLOSED' ? (
            <div className="flex flex-wrap gap-1.5">
              {PRIORITY_OPTIONS.map((p) => {
                const { color, bg } = PRIORITY_STYLES[p];
                const isActive = ticket.priority === p;
                return (
                  <button
                    key={p}
                    onClick={() => !isActive && onPriorityChange(p)}
                    className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-bold transition-all cursor-pointer"
                    style={{
                      backgroundColor: isActive ? bg : 'transparent',
                      color: isActive ? color : 'var(--text-tertiary)',
                      border: `1.5px solid ${isActive ? color : 'var(--border-color)'}`,
                      opacity: isActive ? 1 : 0.65,
                    }}
                  >
                    {p}
                  </button>
                );
              })}
            </div>
          ) : (
            <PriorityBadge priority={ticket.priority} />
          )}
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
            {resolutionNote.reasonCode && (
              <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold mb-1"
                style={{ backgroundColor: 'rgba(16,185,129,0.12)', color: '#10b981' }}>
                {t(`reasonCode.RESOLVE.${resolutionNote.reasonCode}`, { defaultValue: resolutionNote.reasonCode })}
              </span>
            )}
            {resolutionNote.note && (
              <div className="text-xs leading-relaxed whitespace-pre-wrap" style={{ color: 'var(--text-secondary)' }}>
                {resolutionNote.note}
              </div>
            )}
            <div className="text-[11px] mt-1" style={{ color: 'var(--text-tertiary)' }}>
              {resolutionNote.agentName || resolutionNote.agentId} · {formatShortDate(resolutionNote.createdAt)}
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
