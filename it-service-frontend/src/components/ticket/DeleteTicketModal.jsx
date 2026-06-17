import { useTranslation } from 'react-i18next';
import { AlertTriangle, X } from 'lucide-react';
import Button from '../Button';

/**
 * Bilet kalıcı silme onay modalı — yalnızca ADMIN için ExtraActionsModal
 * üzerinden açılır. Backend cascade ile yorum/worklog/csat/attachment kayıtlarını
 * da temizler; geri alma yoktur.
 */
export default function DeleteTicketModal({ isOpen, onClose, onConfirm, deleting, ticketId, ticketTitle }) {
  const { t } = useTranslation();
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={() => !deleting && onClose()}
    >
      <div
        className="w-full max-w-md rounded-xl border animate-slide-up flex flex-col"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-danger-500" />
            <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
              {t('ticketDetail.deleteTicketTitle')}
            </h3>
          </div>
          <button
            onClick={() => !deleting && onClose()}
            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="px-4 sm:px-6 py-5 space-y-3">
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('ticketDetail.deleteTicketDesc')}
          </p>
          {ticketId != null && (
            <div
              className="rounded-lg border px-3 py-2 text-sm font-medium"
              style={{ borderColor: 'var(--border-color)', backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-primary)' }}
            >
              <div>#{ticketId}</div>
              {ticketTitle && (
                <div className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>
                  {ticketTitle}
                </div>
              )}
            </div>
          )}
          <p className="text-xs font-semibold text-danger-600 dark:text-danger-400">
            {t('ticketDetail.deleteTicketWarning')}
          </p>
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
          <Button variant="secondary" disabled={deleting} onClick={onClose}>
            {t('form.cancel')}
          </Button>
          <Button variant="danger" disabled={deleting} onClick={onConfirm}>
            {deleting ? t('ticketDetail.deleteTicketSaving') : t('ticketDetail.deleteTicketConfirm')}
          </Button>
        </div>
      </div>
    </div>
  );
}
