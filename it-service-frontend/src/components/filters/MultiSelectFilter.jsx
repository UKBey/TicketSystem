import { useRef, useState } from 'react';
import { ChevronDown, Check, X, Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import FloatingPanel from './FloatingPanel';

// Seçenek sayısı bunu aşınca dropdown içine arama kutusu eklenir (100 ürün/agent gibi
// uzun listelerde scroll'la aramak yerine yazarak filtrelemek için).
const SEARCH_THRESHOLD = 8;

/**
 * Checkbox tabanlı çoklu seçim dropdown — tüm sayfalarda kullanılan standart filtre bileşeni.
 *
 * Props:
 *   values: string[]              — şu an seçili değerler
 *   onChange: (string[]) => void  — değişiklik callback'i
 *   options: { value, label }[]   — seçenek listesi
 *   placeholder: string           — hiçbir şey seçili değilken görünen metin
 *   disabled?: boolean
 *   disabledHint?: string         — disabled iken gösterilecek placeholder
 *   maxWidth?: string             — buton genişlik üst sınırı (default 180px)
 */
export default function MultiSelectFilter({
  values = [],
  onChange,
  options = [],
  placeholder,
  disabled = false,
  disabledHint,
  maxWidth = '180px',
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const ref = useRef(null);
  const hasValue = values.length > 0;

  const showSearch = options.length > SEARCH_THRESHOLD;
  const visibleOptions = query.trim()
    ? options.filter((o) => String(o.label).toLowerCase().includes(query.trim().toLowerCase()))
    : options;

  const toggle = (val) => {
    if (values.includes(val)) {
      onChange(values.filter((v) => v !== val));
    } else {
      onChange([...values, val]);
    }
  };

  const label = hasValue
    ? values.map((v) => options.find((o) => o.value === v)?.label ?? v).join(', ')
    : (disabled && disabledHint ? disabledHint : placeholder);

  return (
    <div className="relative w-full sm:w-auto" ref={ref}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => { if (disabled) return; if (!open) setQuery(''); setOpen((v) => !v); }}
        className="w-full sm:w-auto sm:min-w-[10rem] sm:max-w-[var(--msf-max-w)] inline-flex items-center justify-between sm:justify-start gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer transition-all focus:outline-none disabled:cursor-not-allowed disabled:opacity-60"
        style={{
          backgroundColor: hasValue ? 'rgba(59,130,246,0.08)' : 'var(--bg-input)',
          borderColor: hasValue ? '#3b82f6' : 'var(--border-color)',
          color: hasValue ? '#2563eb' : 'var(--text-secondary)',
          '--msf-max-w': maxWidth,
        }}
      >
        <span className="truncate">{label}</span>
        <ChevronDown className="h-3 w-3 flex-shrink-0" />
      </button>

      <FloatingPanel
        anchorRef={ref}
        open={open && !disabled}
        onClose={() => setOpen(false)}
        className="rounded-xl border shadow-lg py-1 min-w-[200px] flex flex-col max-h-[320px]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
      >
          {showSearch && (
            <div className="px-2 pb-1.5 pt-0.5">
              <div className="relative">
                <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3 w-3 pointer-events-none" style={{ color: 'var(--text-tertiary)' }} />
                <input
                  autoFocus
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t('filters.searchOptions')}
                  className="w-full rounded-md border pl-7 pr-2 py-1 text-xs outline-none focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
              </div>
            </div>
          )}

          <div className="flex-1 overflow-y-auto min-h-0">
            {visibleOptions.length === 0 && (
              <div className="px-3 py-2 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {t('filters.noOptions')}
              </div>
            )}
            {visibleOptions.map((o) => {
              const checked = values.includes(o.value);
              return (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => toggle(o.value)}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-left cursor-pointer transition-colors hover:bg-primary-50 dark:hover:bg-primary-500/10"
                  style={{ color: checked ? '#2563eb' : 'var(--text-primary)' }}
                >
                  <span
                    className="flex-shrink-0 h-3.5 w-3.5 rounded border flex items-center justify-center"
                    style={{
                      backgroundColor: checked ? '#3b82f6' : 'transparent',
                      borderColor: checked ? '#3b82f6' : 'var(--border-color)',
                    }}
                  >
                    {checked && <Check className="h-2.5 w-2.5 text-white" />}
                  </span>
                  <span className="truncate">{o.label}</span>
                </button>
              );
            })}
          </div>

          {hasValue && (
            <button
              type="button"
              onClick={() => { onChange([]); setOpen(false); }}
              className="flex items-center gap-1 w-full px-3 py-1.5 text-xs cursor-pointer border-t transition-colors hover:bg-danger-50 dark:hover:bg-danger-500/10"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
            >
              <X className="h-3 w-3" />
              {t('filters.clear')}
            </button>
          )}
      </FloatingPanel>
    </div>
  );
}
