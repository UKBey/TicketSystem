import { useTranslation } from 'react-i18next';
import { X, CalendarDays, Check } from 'lucide-react';
import { useEscapeToClose } from '../hooks/useEscapeToClose';
import { useDateFormat } from '../context/DateFormatContext';
import { DATE_FORMATS, formatDate, formatDateTime } from '../utils/dateFormat';

// Önizleme için sabit örnek: 31 Aralık 2026, 14:05.
const SAMPLE_DATE = new Date(2026, 11, 31, 14, 5);

/**
 * Kullanıcının sitedeki tüm tarih gösterimleri için tek-tip formatını seçtiği modal.
 * Seçim anında kaydedilir (DateFormatContext → localStorage + sunucu).
 */
export default function DateFormatModal({ open, onClose }) {
  const { t } = useTranslation();
  const { dateFormat, setDateFormat } = useDateFormat();

  useEscapeToClose(open, onClose);
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        className="w-full max-w-md max-h-[90vh] flex flex-col rounded-2xl border shadow-xl animate-fade-in"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'rgba(59,130,246,0.12)' }}>
            <CalendarDays className="h-4 w-4" style={{ color: '#3b82f6' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-base font-bold leading-tight" style={{ color: 'var(--text-primary)' }}>
              {t('preferences.dateFormat.title')}
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              {t('preferences.dateFormat.desc')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('common.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Options */}
        <div className="p-4 sm:p-5 overflow-y-auto">
          <div className="grid grid-cols-1 gap-2.5">
            {DATE_FORMATS.map((key) => {
              const selected = key === dateFormat;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setDateFormat(key)}
                  aria-pressed={selected}
                  className="flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-left transition-all duration-150 cursor-pointer"
                  style={{
                    borderColor: selected ? '#3b82f6' : 'var(--border-color)',
                    backgroundColor: selected ? 'rgba(59,130,246,0.08)' : 'var(--bg-surface)',
                    boxShadow: selected ? '0 0 0 3px rgba(59,130,246,0.08)' : 'none',
                  }}
                >
                  <div className="min-w-0">
                    <div className="text-sm font-semibold tabular-nums" style={{ color: selected ? '#3b82f6' : 'var(--text-primary)' }}>
                      {formatDate(SAMPLE_DATE, key)}
                    </div>
                    <div className="text-xs mt-0.5 tabular-nums" style={{ color: 'var(--text-tertiary)' }}>
                      {formatDateTime(SAMPLE_DATE, key)}
                    </div>
                  </div>
                  <span
                    className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border"
                    style={{
                      borderColor: selected ? '#3b82f6' : 'var(--border-color)',
                      backgroundColor: selected ? '#3b82f6' : 'transparent',
                    }}
                  >
                    {selected && <Check className="h-3 w-3 text-white" />}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
