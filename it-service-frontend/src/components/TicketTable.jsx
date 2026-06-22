import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { StatusBadge, PriorityBadge } from './Badges';
import SlaTimerBadge from './SlaTimerBadge';
import SortableTh from './SortableTh';
import { useColumnResize } from '../hooks/useColumnResize';
import { formatDateTime } from '../utils/dateFormat';
import { localizedName } from '../utils/localizedName';
import { AlertTriangle, Inbox, Star, ChevronDown, ChevronUp } from 'lucide-react';
import Button from './Button';

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

  const showClaimers = typeof forceShowClaimers === 'boolean'
    ? forceShowClaimers
    : (tickets || []).some((tk) => tk.claimers?.length > 0);
  const sortable = typeof onSort === 'function';
  // Action sütunu kararı: renderActions öncelikli, yoksa shortcut showClaimButton.
  const useCustomActions = typeof renderActions === 'function';
  const showActionsColumn = useCustomActions || Boolean(showClaimButton);

  // Aktif sütunlar colgroup/thead ile AYNI sırada; opsiyonel sütunlar bayrağa göre düşer.
  const columnOrder = [
    'id', 'title', 'status', 'priority',
    showSla && 'sla',
    showClaimers && 'claimers',
    showCsat && 'csat',
    'created',
    showActionsColumn && 'actions',
  ].filter(Boolean);
  // Sürüklenebilir sütun genişlikleri — hook erken return'den önce çağrılmalı.
  // Anahtar tüm ticket listelerinde ortak — genişlikler sütun id'siyle saklandığı
  // için sayfalar arası tutarlı kalır (opsiyonel sütunlar sadece kendi sayfasında görünür).
  const { tableWidth, handleFor, renderColgroup } = useColumnResize(DEFAULT_COL_WIDTHS, columnOrder, 'colw:tickets');


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
            {(localizedName(ticket, 'topicName') || ticket.topicId) && (
              <div className="text-[11px] mb-2" style={{ color: 'var(--text-tertiary)' }}>{localizedName(ticket, 'topicName') || `#${ticket.topicId}`}</div>
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
            <div className="text-[11px] mb-2" style={{ color: 'var(--text-secondary)' }}>{formatDateTime(ticket.createdAt)}</div>
            {showActionsColumn && (
              <div className="flex flex-col gap-2 pt-2 border-t" style={{ borderColor: 'var(--border-color-light)' }}>
                {useCustomActions ? (
                  renderActions(ticket)
                ) : (
                  <>
                    <Button
                      variant="primary"
                      size="sm"
                      fullWidth
                      onClick={(e) => { e.stopPropagation(); onClaim(ticket.id); }}
                    >
                      {t('ticket.actions.claim')}
                    </Button>
                    {showAssignButton && (
                      <Button
                        variant="warning"
                        size="sm"
                        fullWidth
                        onClick={(e) => { e.stopPropagation(); onAssign(ticket); }}
                      >
                        {t('ticket.actions.assign')}
                      </Button>
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
      {/* Sütunlar sürüklenerek yeniden boyutlandırılabilir. Tablo container'ı tam
          doldurur (width:100%); son sütun sabit genişlik almaz, kalan alanı kaplar
          (sağ kenara yapışık). minWidth = sütun toplamı: container darsa o sütun
          default'una iner ve yatay scroll çıkar, içerik ezilmez. */}
      <table className="resizable-table" style={{ tableLayout: 'fixed', width: '100%', minWidth: `${tableWidth}px` }}>
        {renderColgroup()}
        <thead>
          <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
            <SortableTh field="id"          label={t('ticket.table.id')}       sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('id')} />
            <SortableTh field="title"       label={t('ticket.table.title')}    sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('title')} />
            <SortableTh field="status"      label={t('ticket.table.status')}   sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('status')} />
            <SortableTh field="priority"    label={t('ticket.table.priority')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} invertArrow resizeHandle={handleFor('priority')} />
            {showSla && (
              <SortableTh field="slaDeadline" label={t('ticket.table.sla')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('sla')} />
            )}
            {showClaimers && (
              <SortableTh label={t('ticket.table.claimers')} resizeHandle={handleFor('claimers')} />
            )}
            {showCsat && (
              <SortableTh field="csatRating" label={t('ticket.table.csat')} sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('csat')} />
            )}
            <SortableTh field="createdAt"   label={t('ticket.table.created')}  sortBy={sortBy} sortDir={sortDir} onSort={sortable ? onSort : null} resizeHandle={handleFor('created')} />
            {showActionsColumn && (
              <SortableTh label={t('ticket.table.actions')} resizeHandle={handleFor('actions')} />
            )}
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr
              key={ticket.id}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              className="cursor-pointer"
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
                  {showTopic && (localizedName(ticket, 'topicName') || ticket.topicId) && (
                    <span className="block truncate text-[11px] mt-0.5" style={{ color: 'var(--text-tertiary)' }}
                      title={localizedName(ticket, 'topicName') || `#${ticket.topicId}`}>
                      {localizedName(ticket, 'topicName') || `#${ticket.topicId}`}
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
                {formatDateTime(ticket.createdAt)}
              </td>
              {showActionsColumn && (
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {useCustomActions ? (
                      renderActions(ticket)
                    ) : (
                      <>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={(e) => { e.stopPropagation(); onClaim(ticket.id); }}
                        >
                          {t('ticket.actions.claim')}
                        </Button>
                        {showAssignButton && (
                          <Button
                            variant="warning"
                            size="sm"
                            onClick={(e) => { e.stopPropagation(); onAssign(ticket); }}
                          >
                            {t('ticket.actions.assign')}
                          </Button>
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

// Sütunların varsayılan piksel genişlikleri (id ile anahtarlanır). Kullanıcı
// sürükleyip değiştirmediği sürece bunlar geçerlidir; sayfa yenilenince geri gelirler.
const DEFAULT_COL_WIDTHS = {
  id: 90,
  title: 320,
  status: 130,
  priority: 100,
  sla: 130,
  claimers: 140,
  csat: 120,
  created: 140,
  actions: 190,
};

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

// Üstlenen ajan sayısı arttıkça sütun şişmesin: 3'e kadar hepsi gösterilir,
// 4+ olunca kapalıyken yalnız ilk 2 isim + "+N more" (açılıp kapanabilir).
const CLAIMERS_SHOW_ALL_MAX = 3;  // bu sayıya kadar hepsini göster
const CLAIMERS_COLLAPSED = 2;     // fazlaysa kapalıyken kaç isim görünsün

function ClaimerPills({ claimers, currentUserId, t }) {
  const [expanded, setExpanded] = useState(false);

  if (!claimers || claimers.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('ticket.table.unassigned')}</span>;
  }

  const collapsible = claimers.length > CLAIMERS_SHOW_ALL_MAX;
  const visible = collapsible && !expanded ? claimers.slice(0, CLAIMERS_COLLAPSED) : claimers;
  const hiddenCount = claimers.length - CLAIMERS_COLLAPSED;

  return (
    <div className="flex flex-wrap gap-1 items-center">
      {visible.map((c) => (
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

      {collapsible && !expanded && (
        <button
          onClick={(e) => { e.stopPropagation(); setExpanded(true); }}
          className="inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-semibold transition-colors cursor-pointer"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}
        >
          {t('ticket.table.moreClaimers', { count: hiddenCount })}
          <ChevronDown className="h-3 w-3" />
        </button>
      )}

      {collapsible && expanded && (
        <button
          onClick={(e) => { e.stopPropagation(); setExpanded(false); }}
          className="inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-semibold transition-colors cursor-pointer"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}
        >
          {t('ticket.table.showLess')}
          <ChevronUp className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}
