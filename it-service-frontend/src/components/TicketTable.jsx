import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { StatusBadge, PriorityBadge } from './Badges';
import SlaTimerBadge from './SlaTimerBadge';
import SortableTh from './SortableTh';
import { AlertTriangle, Inbox, Star } from 'lucide-react';

export default function TicketTable({
  tickets,
  // Shortcut action props (geriye dönük uyumlu — Pool kullanıyor)
  showClaimButton,
  onClaim,
  showAssignButton = false,
  onAssign,
  showSla = false,
  showCsat = false,         // CSAT yıldız sütunu (yalnızca ADMIN/MANAGER — History/AllTickets)
  currentUserId,
  // Sayfa-spesifik özelleştirme prop'ları
  renderActions,            // (ticket) => ReactNode — verilirse shortcut butonları override eder
  emptyTitle,               // i18n key veya string; default 'ticket.empty.title'
  emptySubtitle,            // i18n key veya string; default 'ticket.empty.subtitle'
  showTopic = true,         // title hücresi altındaki topic alanı (TeamTickets/AllTickets eski davranışı için false)
  forceShowClaimers,        // boolean | undefined — undefined ise auto-detect; sayfa zorlamak isterse boolean
  // Sort props (optional — omit for non-sortable tables)
  sortBy,
  sortDir,
  onSort,
}) {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [tickSeconds, setTickSeconds] = useState(0);

  useEffect(() => {
    if (!showSla) return undefined;
    const timer = setInterval(() => setTickSeconds((v) => v + 1), 1000);
    return () => clearInterval(timer);
  }, [showSla]);

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  };

  if (!tickets || tickets.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-8" style={{ color: 'var(--text-tertiary)' }}>
        <Inbox className="h-12 w-12 mb-4 opacity-30" />
        <h3 className="text-lg font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>
          {t(emptyTitle ?? 'ticket.empty.title')}
        </h3>
        <p className="text-sm">{t(emptySubtitle ?? 'ticket.empty.subtitle')}</p>
      </div>
    );
  }

  const showClaimers = typeof forceShowClaimers === 'boolean'
    ? forceShowClaimers
    : tickets.some((tk) => tk.claimers?.length > 0);
  const sortable = typeof onSort === 'function';

  // Action sütunu kararı: renderActions öncelikli, yoksa shortcut showClaimButton.
  const useCustomActions = typeof renderActions === 'function';
  const showActionsColumn = useCustomActions || Boolean(showClaimButton);

  return (
    <>
      <ul className="lg:hidden space-y-3 p-4">
        {tickets.map((ticket) => (
          <li
            key={ticket.id}
            onClick={() => navigate(`/tickets/${ticket.id}`)}
            className="rounded-xl border p-4 cursor-pointer"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
          >
            <div className="flex items-center justify-between gap-2 mb-2">
              <span className="text-xs font-semibold text-primary-500">TCK-{String(ticket.id).padStart(3, '0')}</span>
              <StatusBadge status={ticket.status} />
            </div>
            <div className="text-sm font-medium break-words mb-1" style={{ color: 'var(--text-primary)' }}>{ticket.title}</div>
            {(ticket.topicName || ticket.topicId) && (
              <div className="text-[11px] mb-2" style={{ color: 'var(--text-tertiary)' }}>{ticket.topicName || `#${ticket.topicId}`}</div>
            )}
            <div className="flex flex-wrap items-center gap-2 mb-2">
              <PriorityBadge priority={ticket.priority} />
              {showSla && <SlaTimerBadge ticket={ticket} tickSeconds={tickSeconds} />}
              {ticket.slaBreached && (
                <span
                  className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold"
                  style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}
                >
                  <AlertTriangle className="h-3 w-3" />SLA
                </span>
              )}
            </div>
            {showClaimers && (
              <div className="mb-2">
                <ClaimerPills claimers={ticket.claimers} currentUserId={currentUserId} t={t} />
              </div>
            )}
            {showCsat && (
              <div className="mb-2">
                <CsatStars rating={ticket.csatRating} t={t} />
              </div>
            )}
            <div className="text-[11px] mb-2" style={{ color: 'var(--text-secondary)' }}>{formatDate(ticket.createdAt)}</div>
            {showActionsColumn && (
              <div className="flex flex-col gap-2 pt-2 border-t" style={{ borderColor: 'var(--border-color-light)' }}>
                {useCustomActions ? (
                  renderActions(ticket)
                ) : (
                  <>
                    <button
                      className="w-full inline-flex items-center justify-center rounded-lg px-3 py-2 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                      onClick={(e) => { e.stopPropagation(); onClaim(ticket.id); }}
                    >
                      {t('ticket.actions.claim')}
                    </button>
                    {showAssignButton && (
                      <button
                        className="w-full inline-flex items-center justify-center rounded-lg px-3 py-2 text-xs font-semibold text-white bg-amber-500 hover:bg-amber-600 transition-colors cursor-pointer"
                        onClick={(e) => { e.stopPropagation(); onAssign(ticket); }}
                      >
                        {t('ticket.actions.assign')}
                      </button>
                    )}
                  </>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>

      {/* lg breakpoint'inden (1024px) sonra tablo görünür. Ama içerik genişliği
          parent container'dan daha fazlaysa (dar sidebar açıkken 1024–1280px arası
          tipik durum), sütunlar daralıp truncate sınırını aşıyordu. overflow-x-auto
          + minWidth tablo intrinsic genişliğini koruyup container içinde yatay
          scroll bar gösterir; üst container'daki rounded-xl overflow-hidden bozulmaz. */}
      <div className="hidden lg:block overflow-x-auto">
      <table className="w-full" style={{ tableLayout: 'fixed', minWidth: tableMinWidth(showSla, showClaimers, showActionsColumn, showCsat) }}>
        <colgroup>
          <col style={{ width: '90px' }} />   {/* ID */}
          <col style={{ width: '30%' }} />    {/* Title — fixed, truncates */}
          <col style={{ width: '130px' }} />  {/* Status */}
          <col style={{ width: '100px' }} />  {/* Priority */}
          {showSla     && <col style={{ width: '130px' }} />}  {/* SLA */}
          {showClaimers && <col style={{ width: '140px' }} />} {/* Claimers */}
          {showCsat    && <col style={{ width: '120px' }} />}  {/* CSAT */}
          <col style={{ width: '140px' }} />  {/* Created */}
          {showActionsColumn && <col style={{ width: '160px' }} />} {/* Action — biraz daha geniş, iki butona yetsin */}
        </colgroup>
        <thead>
          <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <SortableTh field="id"          label={t('ticket.table.id')}       sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortableTh field="title"       label={t('ticket.table.title')}    sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortableTh field="status"      label={t('ticket.table.status')}   sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortableTh field="priority"    label={t('ticket.table.priority')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} invertArrow />
            {showSla && (
              <SortableTh field="slaDeadline" label={t('ticket.table.sla')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            )}
            {showClaimers && (
              <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('ticket.table.claimers')}</th>
            )}
            {showCsat && (
              <SortableTh field="csatRating" label={t('ticket.table.csat')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            )}
            <SortableTh field="createdAt"   label={t('ticket.table.created')}  sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            {showActionsColumn && (
              <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('ticket.table.actions')}</th>
            )}
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr
              key={ticket.id}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="cursor-pointer transition-colors duration-150"
              style={{ borderBottom: '1px solid var(--border-color-light)' }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg-surface-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <td className="px-4 py-3 text-sm font-semibold text-primary-500 truncate">
                TCK-{String(ticket.id).padStart(3, '0')}
              </td>
              <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-primary)' }}>
                <div className="min-w-0">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="font-medium truncate" title={ticket.title}>{ticket.title}</span>
                    {ticket.slaBreached && (
                      <span className="inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold"
                        style={{ backgroundColor: '#fee2e2', color: '#991b1b' }}>
                        <AlertTriangle className="h-3 w-3" />SLA
                      </span>
                    )}
                  </div>
                  {showTopic && (ticket.topicName || ticket.topicId) && (
                    <span className="block truncate text-[11px] mt-0.5" style={{ color: 'var(--text-tertiary)' }}
                      title={ticket.topicName || `#${ticket.topicId}`}>
                      {ticket.topicName || `#${ticket.topicId}`}
                    </span>
                  )}
                </div>
              </td>
              <td className="px-4 py-3"><StatusBadge status={ticket.status} /></td>
              <td className="px-4 py-3"><PriorityBadge priority={ticket.priority} /></td>
              {showSla && (
                <td className="px-4 py-3">
                  <SlaTimerBadge ticket={ticket} tickSeconds={tickSeconds} />
                </td>
              )}
              {showClaimers && (
                <td className="px-4 py-3">
                  <ClaimerPills claimers={ticket.claimers} currentUserId={currentUserId} t={t} />
                </td>
              )}
              {showCsat && (
                <td className="px-4 py-3">
                  <CsatStars rating={ticket.csatRating} t={t} />
                </td>
              )}
              <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                {formatDate(ticket.createdAt)}
              </td>
              {showActionsColumn && (
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {useCustomActions ? (
                      renderActions(ticket)
                    ) : (
                      <>
                        <button
                          className="inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                          onClick={(e) => { e.stopPropagation(); onClaim(ticket.id); }}
                        >
                          {t('ticket.actions.claim')}
                        </button>
                        {showAssignButton && (
                          <button
                            className="inline-flex items-center rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-amber-500 hover:bg-amber-600 transition-colors cursor-pointer"
                            onClick={(e) => { e.stopPropagation(); onAssign(ticket); }}
                          >
                            {t('ticket.actions.assign')}
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </>
  );
}

/**
 * Tablo için ihtiyaca göre minimum genişlik. Title sütununun (%30) gerçek
 * piksel değeri, diğer sabit sütunların toplamından sonra %70 dilim olarak
 * ortaya çıkıyor → minWidth = (fixedSum) / 0.70 ile başlığa makul (≥200px)
 * yer kalır.
 */
function tableMinWidth(showSla, showClaimers, showActions, showCsat) {
  let fixed = 90 + 130 + 100 + 140; // ID + Status + Priority + Created
  if (showSla) fixed += 130;
  if (showClaimers) fixed += 140;
  if (showCsat) fixed += 120;
  if (showActions) fixed += 160;
  return `${Math.ceil(fixed / 0.70)}px`;
}

/** CSAT yıldız puanı; puan yoksa nötr bir tire gösterir. */
function CsatStars({ rating, t }) {
  if (rating == null) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }} title={t('ticket.table.csatNone')}>—</span>;
  }
  return (
    <span className="inline-flex items-center gap-0.5" title={`${rating}/5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star
          key={n}
          className="h-3.5 w-3.5"
          style={{ color: '#f59e0b', fill: n <= rating ? '#f59e0b' : 'transparent' }}
        />
      ))}
    </span>
  );
}

function ClaimerPills({ claimers, currentUserId, t }) {
  if (!claimers || claimers.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.table.unassigned')}</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {claimers.map((c) => (
        <span
          key={c.agentId}
          title={c.agentName}
          className="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
          style={
            c.agentId === currentUserId
              ? { backgroundColor: '#dbeafe', color: '#1d4ed8' }
              : { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }
          }
        >
          {c.agentName?.split(' ')[0] ?? 'Agent'}
          {c.agentId === currentUserId && ` (${t('ticket.table.you')})`}
        </span>
      ))}
    </div>
  );
}
