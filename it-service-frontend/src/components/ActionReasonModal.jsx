import { useEffect, useMemo, useState } from 'react';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const VARIANT_STYLES = {
  primary: 'bg-primary-500 hover:bg-primary-600 focus:ring-primary-500/30',
  danger: 'bg-danger-500 hover:bg-danger-600 focus:ring-danger-500/30',
  success: 'bg-green-600 hover:bg-green-700 focus:ring-green-500/30',
  warning: 'bg-amber-500 hover:bg-amber-600 focus:ring-amber-500/30',
};

export default function ActionReasonModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel,
  confirmVariant,
}) {
  const { t } = useTranslation();
  const [noteText, setNoteText] = useState('');

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const isSubmitDisabled = noteText.trim().length === 0;
  const characterCount = noteText.trim().length;
  const actionButtonClass = useMemo(() => {
    return VARIANT_STYLES[confirmVariant] ?? VARIANT_STYLES.primary;
  }, [confirmVariant]);

  const handleSubmit = (event) => {
    event.preventDefault();
    const trimmedNote = noteText.trim();

    if (!trimmedNote) {
      return;
    }

    onConfirm(trimmedNote);
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-xl border animate-slide-up"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-4 px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
          <div>
            <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{title}</h3>
            <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>{description}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('ticket.actionModal.closeModal')}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="px-6 py-5 space-y-4">
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('ticket.actionModal.labelReason')} *
              </label>
              <textarea
                value={noteText}
                onChange={(event) => setNoteText(event.target.value)}
                rows={4}
                placeholder={t('ticket.actionModal.placeholderReason')}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[110px]"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              />
              <div className="mt-2 flex items-center justify-between text-xs" style={{ color: 'var(--text-tertiary)' }}>
                <span>{t('ticket.actionModal.hint')}</span>
                <span>{t('ticket.actionModal.characters', { count: characterCount })}</span>
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
            >
              {t('form.cancel')}
            </button>
            <button
              type="submit"
              disabled={isSubmitDisabled}
              className={`rounded-lg px-4 py-2 text-sm font-semibold text-white transition-colors focus:outline-none focus:ring-4 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer ${actionButtonClass}`}
            >
              {confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
