import { useTranslation } from 'react-i18next';
import { Settings2 } from 'lucide-react';
import Button from '../Button';

export default function StatusActionsCard({
  ticket, user, allowedStatuses,
  isAgent, canAssign, canDelete,
  onWaiting, onResume, onReopen, onClaim, onResolveClick,
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
              <Button
                variant={ticket.status === 'WAITING_FOR_CUSTOMER' ? 'primary' : 'secondary'}
                size="sm"
                fullWidth
                className="sm:flex-1 min-h-[40px]"
                onClick={() => ticket.status === 'WAITING_FOR_CUSTOMER' ? onResume() : onWaiting()}
              >
                {ticket.status === 'WAITING_FOR_CUSTOMER' ? t('ticketDetail.resume') : t('ticketDetail.waiting')}
              </Button>
            )}
            {(allowedStatuses.includes('RESOLVED') || ticket.status === 'RESOLVED') && (
              <Button
                variant={ticket.status === 'RESOLVED' ? 'danger' : 'accent'}
                size="sm"
                fullWidth
                className="sm:flex-1 min-h-[40px]"
                onClick={() => ticket.status === 'RESOLVED' ? onReopen() : onResolveClick()}
              >
                {ticket.status === 'RESOLVED' ? t('ticketDetail.reopen') : t('ticketDetail.resolve')}
              </Button>
            )}
          </div>
        )}

        {isAgent && !hasClaimed && ticket?.status !== 'CLOSED' && (
          <Button
            variant={noClaimer ? 'primary' : 'accent'}
            size="sm"
            fullWidth
            className="min-h-[40px]"
            onClick={onClaim}
          >
            {noClaimer ? t('ticketDetail.claim') : t('ticketDetail.join')}
          </Button>
        )}

        {canAssign && ticket.status !== 'CLOSED' && (
          <Button
            variant="warning"
            size="sm"
            fullWidth
            className="min-h-[40px]"
            onClick={() => onSetAssignModal(true)}
          >
            {t('ticketDetail.assignToAgent')}
          </Button>
        )}

        {hasExtraActions && (
          <Button
            variant="secondary"
            size="sm"
            fullWidth
            className="min-h-[40px]"
            onClick={() => onExtraActionsOpen(true)}
          >
            <Settings2 className="h-3.5 w-3.5" />
            {t('ticketDetail.extraActions')}
          </Button>
        )}
      </div>
    </div>
  );
}
