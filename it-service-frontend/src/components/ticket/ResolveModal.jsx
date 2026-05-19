import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { OTHER_REASON_CODE, REASON_CODES } from '../../utils/reasonCodes';
import { useToast } from '../../context/ToastContext';

export default function ResolveModal({ isOpen, onClose, onSave }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [reasonCode, setReasonCode] = useState('');
  const [noteText, setNoteText] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setReasonCode('');
      setNoteText('');
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const isOther = reasonCode === OTHER_REASON_CODE;
  const trimmedNote = noteText.trim();
  const isSubmitDisabled = saving || !reasonCode || (isOther && trimmedNote.length === 0);

  const handleSave = async () => {
    if (isSubmitDisabled) return;
    setSaving(true);
    try {
      await onSave({ reasonCode, note: trimmedNote || null });
    } catch (err) {
      toast.error(err.response?.data?.message || t('ticketDetail.resolveFailed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={() => !saving && onClose()}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('ticketDetail.resolveModalTitle')}
          </h3>
          <button
            onClick={() => !saving && onClose()}
            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto flex-1">
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('ticketDetail.resolveModalDesc')}
          </p>
          <div>
            <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
              {t('ticket.actionModal.labelReason')} *
            </label>
            <select
              value={reasonCode}
              onChange={(e) => setReasonCode(e.target.value)}
              disabled={saving}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            >
              <option value="">{t('ticket.actionModal.selectReason')}</option>
              {REASON_CODES.RESOLVE.map((code) => (
                <option key={code} value={code}>
                  {t(`reasonCode.RESOLVE.${code}`)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
              {t('ticket.actionModal.labelNote')} {isOther && '*'}
            </label>
            <textarea
              placeholder={isOther ? t('ticket.actionModal.placeholderNoteOther') : t('ticket.actionModal.placeholderNoteOptional')}
              rows="3"
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              disabled={saving}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[90px]"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            />
            <div className="mt-1.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
              {isOther ? t('ticket.actionModal.hintOther') : t('ticket.actionModal.hintOptional')}
            </div>
          </div>
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <button
            disabled={saving}
            onClick={() => onClose()}
            className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
          >
            {t('form.cancel')}
          </button>
          <button
            disabled={isSubmitDisabled}
            onClick={handleSave}
            className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-accent-500 hover:bg-accent-600 transition-colors disabled:opacity-50 cursor-pointer"
          >
            {saving ? t('ticketDetail.resolveModalSaving') : t('ticketDetail.resolveModalSave')}
          </button>
        </div>
      </div>
    </div>
  );
}
