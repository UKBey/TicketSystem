import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import Button from '../Button';

export default function ExtraActionsModal({
  isOpen, onClose,
  ticket, user, allowedStatuses, canDelete,
  openReasonModal,
  onDeleteClick,
}) {
  const { t } = useTranslation();
  if (!isOpen) return null;

  const currentUserId = user?.sub || user?.id;
  const hasClaimed    = ticket?.claimers?.some((c) => c.agentId === currentUserId);

  // Unclaim, aktif claim'i olan ve kapalı olmayan her bilette mümkün — RESOLVED dahil
  // (backend statüyü değiştirmeden claim'i bırakır).
  const showUnclaim = (allowedStatuses.includes('NEW') || ticket?.status === 'WAITING_FOR_CUSTOMER' || ticket?.status === 'RESOLVED') && hasClaimed;
  const showClose   = allowedStatuses.includes('CLOSED') && (!canDelete || hasClaimed);
  // Silme yalnızca ADMIN için; claim gerekmez, statüden bağımsız.
  const showDelete  = canDelete;
  const noActions   = !showUnclaim && !showClose && !showDelete;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.extraActions')}</h3>
          <button onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-4 sm:p-5 space-y-3 overflow-y-auto flex-1">
          {showUnclaim && (
            <button
              className="w-full rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
              onClick={() => openReasonModal('UNCLAIM')}
            >
              {t('ticketDetail.unclaimRelease')}
            </button>
          )}
          {showClose && (
            <Button
              variant="danger"
              fullWidth
              onClick={() => openReasonModal('CLOSE')}
            >
              {t('ticketDetail.closeTicket')}
            </Button>
          )}
          {showDelete && (
            <button
              className="w-full rounded-lg border-2 border-danger-500 px-4 py-2.5 text-sm font-semibold text-danger-600 dark:text-danger-400 hover:bg-danger-50 dark:hover:bg-danger-500/10 transition-colors cursor-pointer"
              onClick={onDeleteClick}
            >
              {t('ticketDetail.deleteTicket')}
            </button>
          )}
          {noActions && (
            <div className="text-center py-4 text-sm" style={{ color: 'var(--text-tertiary)' }}>
              {t('ticketDetail.noExtraActions')}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
