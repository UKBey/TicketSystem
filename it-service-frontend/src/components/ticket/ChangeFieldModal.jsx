import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { OTHER_REASON_CODE } from '../../utils/reasonCodes';

export default function ChangeFieldModal({
  isOpen,
  onClose,
  onSave,
  title,
  description,
  label,
  options,
  currentValue,
  loading = false,
  reasonCodes,
  reasonTranslationPrefix,
}) {
  const { t } = useTranslation();
  const [value, setValue]           = useState(currentValue ?? '');
  const [reasonCode, setReasonCode] = useState('');
  const [noteText, setNoteText]     = useState('');
  const [saving, setSaving]         = useState(false);

  useEffect(() => {
    if (isOpen) {
      setValue(currentValue ?? '');
      setReasonCode('');
      setNoteText('');
    }
  }, [isOpen, currentValue]);

  useEffect(() => {
    if (!isOpen) return undefined;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const isOther       = reasonCode === OTHER_REASON_CODE;
  const trimmedNote   = noteText.trim();
  const unchanged     = String(value) === String(currentValue ?? '');
  const noteRequired  = isOther && trimmedNote.length === 0;
  const disabled = saving || loading || unchanged || !value || !reasonCode || noteRequired;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (disabled) return;
    setSaving(true);
    try {
      await onSave({ value, reasonCode, note: trimmedNote || null });
      onClose();
    } finally {
      setSaving(false);
    }
  };

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
        <div className="flex items-start justify-between gap-3 sm:gap-4 px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <div className="min-w-0">
            <h3 className="text-lg font-bold break-words" style={{ color: 'var(--text-primary)' }}>{title}</h3>
            {description && (
              <p className="mt-1 text-sm break-words" style={{ color: 'var(--text-secondary)' }}>{description}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
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
                {label} *
              </label>
              <select
                value={value}
                onChange={(e) => setValue(e.target.value)}
                disabled={loading}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer disabled:opacity-60"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              >
                {loading ? (
                  <option value="">{t('common.loading')}</option>
                ) : (
                  options.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))
                )}
              </select>
            </div>

            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('ticket.actionModal.labelReason')} *
              </label>
              <select
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              >
                <option value="">{t('ticket.actionModal.selectReason')}</option>
                {(reasonCodes || []).map((code) => (
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
                onChange={(e) => setNoteText(e.target.value)}
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
              disabled={disabled}
              className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {saving ? t('form.saving') : t('form.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
