import { useTranslation } from 'react-i18next';
import { Settings2 } from 'lucide-react';

export default function StatusActionsCard({
  ticket, user, allowedStatuses,
  isAgent, canAssign, canDelete,
  onStatusChange, onClaim, onResolveClick,
  onSetAssignModal, onExtraActionsOpen,
}) {
  const { t } = useTranslation();
  const currentUserId = user?.sub || user?.id;
  const hasClaimed = ticket?.claimers?.some((c) => c.agentId === currentUserId);
  // Statü aksiyonları (Resume/Waiting/Resolve) için claim şarttır: claim almamış sade
  // ajan bu aksiyonları görmez (önce Üstlen/Katıl). Tek istisna lead_agent — ürünleri
  // içinde claim almadan mutasyon yapabilir (canAssign && !canDelete = lead, admin değil).
  // Pure admin de claim almalıdır.
  const canActWithoutClaim = canAssign && !canDelete;
  const canDoStatusActions = hasClaimed || canActWithoutClaim;
  const noClaimer = !ticket?.claimers || ticket.claimers.length === 0;

  // Unclaim, aktif claim'i olan ve kapalı olmayan her bilette mümkün. RESOLVED'de bilet
  // çözümde kalır (backend statüyü değiştirmez); WAITING ise IN_PROGRESS'e dönmeden bırakılır.
  const hasUnclaim = (allowedStatuses.includes('NEW') || ticket?.status === 'WAITING_FOR_CUSTOMER' || ticket?.status === 'RESOLVED') && hasClaimed;
  const hasClose   = allowedStatuses.includes('CLOSED') && (!canDelete || hasClaimed);
  // Admin her statüde silebildiği için Extra Actions düğmesi onun için her
  // zaman erişilebilir; diğer roller yalnızca unclaim/close aksiyonu varsa görür.
  const hasExtraActions = hasUnclaim || hasClose || canDelete;

  // Admin için CLOSED bilet gibi statü geçişi olmayan durumlarda da
  // kartın görünmesi gerekir — aksi halde "Delete" aksiyonuna ulaşamaz.
  if (!isAgent && !canDelete) return null;
  if (allowedStatuses.length === 0 && !canDelete) return null;

  return (
    <div className="rounded-xl border" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="px-5 py-3 border-b text-sm font-semibold" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
        {t('ticketDetail.statusActions')}
      </div>
      <div className="p-4 space-y-2">
        {canDoStatusActions && (
          <div className="flex flex-col sm:flex-row gap-2">
            {(allowedStatuses.includes('WAITING_FOR_CUSTOMER') || ticket.status === 'WAITING_FOR_CUSTOMER') && (
              <button
                className={`w-full sm:flex-1 rounded-lg px-3 py-2 min-h-[40px] text-xs font-semibold transition-colors cursor-pointer ${
                  ticket.status === 'WAITING_FOR_CUSTOMER' ? 'bg-primary-500 text-white' : 'border'
                }`}
                style={ticket.status !== 'WAITING_FOR_CUSTOMER' ? { borderColor: 'var(--border-color)', color: 'var(--text-secondary)' } : {}}
                onClick={() => onStatusChange(ticket.status === 'WAITING_FOR_CUSTOMER' ? 'IN_PROGRESS' : 'WAITING_FOR_CUSTOMER')}
              >
                {ticket.status === 'WAITING_FOR_CUSTOMER' ? t('ticketDetail.resume') : t('ticketDetail.waiting')}
              </button>
            )}
            {(allowedStatuses.includes('RESOLVED') || ticket.status === 'RESOLVED') && (
              <button
                className={`w-full sm:flex-1 rounded-lg px-3 py-2 min-h-[40px] text-xs font-semibold transition-colors cursor-pointer ${
                  ticket.status === 'RESOLVED'
                    ? 'bg-danger-500 text-white hover:bg-danger-600'
                    : 'bg-accent-500 text-white hover:bg-accent-600'
                }`}
                onClick={() => ticket.status === 'RESOLVED' ? onStatusChange('IN_PROGRESS') : onResolveClick()}
              >
                {ticket.status === 'RESOLVED' ? t('ticketDetail.reopen') : t('ticketDetail.resolve')}
              </button>
            )}
          </div>
        )}

        {isAgent && !hasClaimed && ticket?.status !== 'CLOSED' && (
          <button
            className={`w-full rounded-lg px-3 py-2 min-h-[40px] text-xs font-semibold text-white transition-colors cursor-pointer ${
              noClaimer ? 'bg-primary-500 hover:bg-primary-600' : 'bg-accent-500 hover:bg-accent-600'
            }`}
            onClick={onClaim}
          >
            {noClaimer ? t('ticketDetail.claim') : t('ticketDetail.join')}
          </button>
        )}

        {canAssign && ticket.status !== 'CLOSED' && (
          <button
            className="w-full rounded-lg px-3 py-2 min-h-[40px] text-xs font-semibold text-white bg-amber-500 hover:bg-amber-600 transition-colors cursor-pointer"
            onClick={() => onSetAssignModal(true)}
          >
            {t('ticketDetail.assignToAgent')}
          </button>
        )}

        {hasExtraActions && (
          <button
            className="w-full rounded-lg border px-3 py-2 min-h-[40px] text-xs font-medium transition-colors cursor-pointer flex items-center justify-center gap-1.5"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
            onClick={() => onExtraActionsOpen(true)}
          >
            <Settings2 className="h-3.5 w-3.5" />
            {t('ticketDetail.extraActions')}
          </button>
        )}
      </div>
    </div>
  );
}
