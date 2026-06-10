import { useRef, useState } from 'react';
import { Search, X } from 'lucide-react';

/**
 * Debounce'lu arama input'u (500 ms) — tüm filtre barlarında kullanılan standart.
 *
 * Input remount edilmeden çalışır: yazarken focus korunur. `value` prop'u dışarıdan
 * (clearFilters / chip X) değişirse local state senkronlanır; kullanıcının debounce ile
 * henüz iletilmemiş metni `lastEmitted` ile korunur (üzerine yazılmaz).
 */
export default function FilterSearchInput({
  value,
  onChange,
  placeholder,
  width = '11rem',
  debounceMs = 500,
  maxLength = 100,
}) {
  const [local, setLocal] = useState(value ?? '');
  const [prevValue, setPrevValue] = useState(value ?? '');
  const [lastEmitted, setLastEmitted] = useState(value ?? '');
  const timer = useRef(null);

  // Prop'tan state türetme (React'in render sırasında ayarlama deseni): yalnızca
  // dışarıdan gelen (bizim debounce ile emit etmediğimiz) bir `value` değişikliğinde
  // local'i senkronla — remount yok, dolayısıyla focus korunur.
  const incoming = value ?? '';
  if (incoming !== prevValue) {
    setPrevValue(incoming);
    if (incoming !== lastEmitted) setLocal(incoming);
  }

  const handleChange = (e) => {
    const v = e.target.value;
    setLocal(v);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => { setLastEmitted(v); onChange(v); }, debounceMs);
  };

  return (
    <div className="relative w-full sm:w-auto" style={{ '--fsi-w': width }}>
      <Search
        className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2"
        style={{ color: 'var(--text-tertiary)' }}
      />
      <input
        type="text"
        value={local}
        onChange={handleChange}
        placeholder={placeholder}
        maxLength={maxLength}
        className="w-full sm:w-[var(--fsi-w)] rounded-lg border pl-8 pr-7 py-1.5 text-xs outline-none transition-all focus:ring-2"
        style={{
          backgroundColor: local ? 'rgba(59,130,246,0.06)' : 'var(--bg-input)',
          borderColor: local ? '#3b82f6' : 'var(--border-color)',
          color: 'var(--text-primary)',
        }}
      />
      {local && (
        <button
          type="button"
          onClick={() => { setLocal(''); setLastEmitted(''); clearTimeout(timer.current); onChange(''); }}
          className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer"
          style={{ color: 'var(--text-tertiary)' }}
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}
