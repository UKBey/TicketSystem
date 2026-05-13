import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';

export default function ResolveModal({ isOpen, onClose, existingNote, onSave }) {
  const { t } = useTranslation();
  const [noteText, setNoteText] = useState('');
  const [saving, setSaving]     = useState(false);

  useEffect(() => {
    if (isOpen) setNoteText(existingNote?.note || '');
  }, [isOpen, existingNote]);

  if (!isOpen) return null;

  const handleSave = async () => {
    if (!noteText.trim()) return;
    setSaving(true);
    try {
      await onSave(noteText);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not save resolution note or update status.');
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
        className="w-full max-w-md rounded-xl border animate-slide-up"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
            {existingNote ? t('ticketDetail.resolveModalUpdateTitle') : t('ticketDetail.resolveModalTitle')}
          </h3>
          <button onClick={() => !saving && onClose()} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {t('ticketDetail.resolveModalDesc')}
          </p>
          <textarea
            placeholder={t('ticketDetail.resolveModalPlaceholder')}
            rows="4"
            value={noteText}
            onChange={(e) => setNoteText(e.target.value)}
            disabled={saving}
            className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[100px]"
            style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
          />
        </div>

        <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
          <button
            disabled={saving}
            onClick={() => onClose()}
            className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
          >
            {t('form.cancel')}
          </button>
          <button
            disabled={saving || !noteText.trim()}
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
