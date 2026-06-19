import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Aktif filtre özet etiketi — tıklanınca ilgili filtreyi temizler.
 */
export default function FilterChip({ label, onRemove }) {
  const { t } = useTranslation();
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium"
      style={{
        backgroundColor: 'rgba(59,130,246,0.08)',
        borderColor: 'rgba(59,130,246,0.2)',
        color: '#2563eb',
      }}
    >
      {label}
      <button
        type="button"
        onClick={onRemove}
        className="cursor-pointer hover:opacity-70"
        aria-label={t('common.remove')}
      >
        <X className="h-2.5 w-2.5" />
      </button>
    </span>
  );
}
