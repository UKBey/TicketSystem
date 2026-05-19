import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { StatusBadge, PriorityBadge } from './Badges';
import SlaTimerBadge from './SlaTimerBadge';
import { AlertTriangle, ArrowUpDown, ArrowUp, ArrowDown, Inbox } from 'lucide-react';

export default function TicketTable({
  tickets,
  showClaimButton,
  onClaim,
  showSla = false,
  currentUserId,
  showAssignButton = false,
  onAssign,
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
        <h3 className="text-lg font-semibold mb-1" style={{ color: 'var(--text-primary)' }}>{t('ticket.empty.title')}</h3>
        <p className="text-sm">{t('ticket.empty.subtitle')}</p>
      </div>
    );
  }

  const showClaimers = tickets.some((tk) => tk.claimers?.length > 0);
  const sortable = typeof onSort === 'function';

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
            <div className="text-[11px] mb-2" style={{ color: 'var(--text-secondary)' }}>{formatDate(ticket.createdAt)}</div>
            {showClaimButton && (
              <div className="flex flex-col gap-2 pt-2 border-t" style={{ borderColor: 'var(--border-color-light)' }}>
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
      <table className="w-full" style={{ tableLayout: 'fixed', minWidth: tableMinWidth(showSla, showClaimers, showClaimButton) }}>
        <colgroup>
          <col style={{ width: '90px' }} />   {/* ID */}
          <col style={{ width: '30%' }} />    {/* Title — fixed, truncates */}
          <col style={{ width: '130px' }} />  {/* Status */}
          <col style={{ width: '100px' }} />  {/* Priority */}
          {showSla     && <col style={{ width: '130px' }} />}  {/* SLA */}
          {showClaimers && <col style={{ width: '140px' }} />} {/* Claimers */}
          <col style={{ width: '140px' }} />  {/* Created */}
          {showClaimButton && <col style={{ width: '120px' }} />} {/* Action */}
        </colgroup>
        <thead>
          <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <SortTh field="id"          label={t('ticket.table.id')}       sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortTh field="title"       label={t('ticket.table.title')}    sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortTh field="status"      label={t('ticket.table.status')}   sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            <SortTh field="priority"    label={t('ticket.table.priority')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} invertArrow />
            {showSla && (
              <SortTh field="slaDeadline" label={t('ticket.table.sla')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            )}
            {showClaimers && (
              <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('ticket.table.claimers')}</th>
            )}
            <SortTh field="createdAt"   label={t('ticket.table.created')}  sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} />
            {showClaimButton && (
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
                  {(ticket.topicName || ticket.topicId) && (
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
              <td className="px-4 py-3 text-sm" style={{ color: 'var(--text-secondary)' }}>
                {formatDate(ticket.createdAt)}
              </td>
              {showClaimButton && (
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
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
function tableMinWidth(showSla, showClaimers, showClaim) {
  let fixed = 90 + 130 + 100 + 140; // ID + Status + Priority + Created
  if (showSla) fixed += 130;
  if (showClaimers) fixed += 140;
  if (showClaim) fixed += 120;
  return `${Math.ceil(fixed / 0.70)}px`;
}

function SortTh({ field, label, sortBy, sortDir, onSort, invertArrow = false }) {
  const active = sortBy === field;

  // invertArrow: priority gibi alanlarda görsel ok yönü tersine çevrilir.
  // "asc" backend'de LOW→CRITICAL (düşük öncelik → yüksek öncelik) demek,
  // ama kullanıcı "yukarı ok = CRITICAL üstte" bekler → ok tersine gösterilir.
  const displayDir = invertArrow
    ? (sortDir === 'asc' ? 'desc' : 'asc')
    : sortDir;

  const Icon = active
    ? (displayDir === 'asc' ? ArrowUp : ArrowDown)
    : ArrowUpDown;

  if (!onSort) {
    return (
      <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
        style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
        {label}
      </th>
    );
  }

  return (
    <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className="inline-flex items-center gap-1 cursor-pointer hover:opacity-80 transition-opacity"
        style={{ color: active ? '#3b82f6' : 'var(--text-tertiary)' }}
      >
        {label}
        <Icon className="h-3 w-3" />
      </button>
    </th>
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
