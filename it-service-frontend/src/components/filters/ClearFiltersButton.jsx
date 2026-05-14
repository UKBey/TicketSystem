import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Filtre barlarında "tümünü temizle" butonu — tek tip stil.
 */
export default function ClearFiltersButton({ onClick, label }) {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
      style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
    >
      <X className="h-3 w-3" />
      {label ?? t('filters.clearAll')}
    </button>
  );
}
