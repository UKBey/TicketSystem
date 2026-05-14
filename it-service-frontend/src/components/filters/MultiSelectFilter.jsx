import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Check, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

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
  const [alignRight, setAlignRight] = useState(false);
  const ref = useRef(null);
  const buttonRef = useRef(null);
  const hasValue = values.length > 0;

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Dropdown açıldığında ekrana sığmıyorsa otomatik sağa hizala.
  useEffect(() => {
    if (!open || !buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    const DROPDOWN_MIN_WIDTH = 200;
    setAlignRight(rect.left + DROPDOWN_MIN_WIDTH > window.innerWidth - 8);
  }, [open]);

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
    <div className="relative" ref={ref}>
      <button
        ref={buttonRef}
        type="button"
        disabled={disabled}
        onClick={() => !disabled && setOpen((v) => !v)}
        className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer transition-all focus:outline-none disabled:cursor-not-allowed disabled:opacity-60"
        style={{
          backgroundColor: hasValue ? 'rgba(59,130,246,0.08)' : 'var(--bg-input)',
          borderColor: hasValue ? '#3b82f6' : 'var(--border-color)',
          color: hasValue ? '#2563eb' : 'var(--text-secondary)',
          maxWidth,
        }}
      >
        <span className="truncate">{label}</span>
        <ChevronDown className="h-3 w-3 flex-shrink-0" />
      </button>

      {open && !disabled && (
        <div
          className={`absolute ${alignRight ? 'right-0' : 'left-0'} top-full mt-1 z-50 rounded-xl border shadow-lg py-1 min-w-[180px] max-h-[300px] overflow-y-auto`}
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        >
          {options.length === 0 && (
            <div className="px-3 py-2 text-xs" style={{ color: 'var(--text-tertiary)' }}>
              {t('filters.noOptions')}
            </div>
          )}
          {options.map((o) => {
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
          {hasValue && (
            <button
              type="button"
              onClick={() => { onChange([]); setOpen(false); }}
              className="flex items-center gap-1 w-full px-3 py-1.5 text-xs cursor-pointer border-t transition-colors hover:bg-danger-50 dark:hover:bg-danger-500/10 mt-0.5"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
            >
              <X className="h-3 w-3" />
              {t('filters.clear')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
