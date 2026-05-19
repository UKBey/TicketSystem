import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Star } from 'lucide-react';
import { useToast } from '../../context/ToastContext';

export default function CsatModal({ isOpen, onClose, onSubmit }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [rating, setRating]     = useState(5);
  const [comment, setComment]   = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await onSubmit(rating, comment);
    } catch (err) {
      toast.error(err.response?.data?.message || t('ticketDetail.submitCsatFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={() => !submitting && onClose()}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticketDetail.csatTitle')}</h3>
          <button onClick={() => !submitting && onClose()} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto flex-1">
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>{t('ticketDetail.csatQuestion')}</p>
          <div className="flex gap-2 justify-center py-2">
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                type="button"
                className="transition-all duration-200 hover:scale-110 cursor-pointer"
                style={{ fontSize: '32px', background: 'none', border: 'none', outline: 'none', color: rating >= star ? '#f59e0b' : 'var(--text-tertiary)' }}
                onClick={() => setRating(star)}
              >
                <Star className="h-8 w-8" fill={rating >= star ? '#f59e0b' : 'none'} />
              </button>
            ))}
          </div>
          <textarea
            placeholder={t('ticketDetail.csatPlaceholder')}
            rows="3"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y"
            style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
          />
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <button
            disabled={submitting}
            onClick={() => onClose()}
            className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
          >
            {t('form.cancel')}
          </button>
          <button
            disabled={submitting}
            onClick={handleSubmit}
            className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
          >
            {t('ticketDetail.csatSubmit')}
          </button>
        </div>
      </div>
    </div>
  );
}
