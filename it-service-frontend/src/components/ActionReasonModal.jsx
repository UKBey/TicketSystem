import { useState } from 'react';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { OTHER_REASON_CODE } from '../utils/reasonCodes';
import { useEscapeToClose } from '../hooks/useEscapeToClose';
import Button from './Button';

export default function ActionReasonModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel,
  confirmVariant,
  reasonCodes,
  reasonTranslationPrefix,
}) {
  const { t } = useTranslation();
  const [reasonCode, setReasonCode] = useState('');
  const [noteText, setNoteText] = useState('');

  useEscapeToClose(isOpen, onClose);

  const resetAndClose = () => {
    setReasonCode('');
    setNoteText('');
    onClose();
  };

  const isOther = reasonCode === OTHER_REASON_CODE;
  const trimmedNote = noteText.trim();
  const isSubmitDisabled = !reasonCode || (isOther && trimmedNote.length === 0);

  const handleSubmit = (event) => {
    event.preventDefault();
    if (isSubmitDisabled) return;
    onConfirm({ reasonCode, note: trimmedNote || null });
    setReasonCode('');
    setNoteText('');
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={resetAndClose}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 sm:gap-4 px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <div className="min-w-0">
            <h3 className="text-lg font-bold break-words" style={{ color: 'var(--text-primary)' }}>{title}</h3>
            <p className="mt-1 text-sm break-words" style={{ color: 'var(--text-secondary)' }}>{description}</p>
          </div>
          <button
            type="button"
            onClick={resetAndClose}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('ticket.actionModal.closeModal')}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col min-h-0 flex-1">
          <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto flex-1">
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('ticket.actionModal.labelReason')} *
              </label>
              <select
                value={reasonCode}
                onChange={(event) => setReasonCode(event.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              >
                <option value="">{t('ticket.actionModal.selectReason')}</option>
                {reasonCodes?.map((code) => (
                  <option key={code} value={code}>
                    {t(`${reasonTranslationPrefix}.${code}`)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('ticket.actionModal.labelNote')} {isOther && '*'}
              </label>
              <textarea
                value={noteText}
                onChange={(event) => setNoteText(event.target.value)}
                rows={3}
                placeholder={isOther ? t('ticket.actionModal.placeholderNoteOther') : t('ticket.actionModal.placeholderNoteOptional')}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[90px]"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              />
              <div className="mt-1.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {isOther ? t('ticket.actionModal.hintOther') : t('ticket.actionModal.hintOptional')}
              </div>
            </div>
          </div>

          <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <Button type="button" variant="secondary" onClick={resetAndClose}>
              {t('form.cancel')}
            </Button>
            <Button type="submit" variant={confirmVariant ?? 'primary'} disabled={isSubmitDisabled}>
              {confirmLabel}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
