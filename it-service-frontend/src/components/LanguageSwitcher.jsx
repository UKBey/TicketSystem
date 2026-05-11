import { useRef, useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, Check } from 'lucide-react';
import api from '../services/api';

const LANGUAGES = [
  { code: 'en', label: 'English', flag: '🇺🇸' },
  { code: 'tr', label: 'Türkçe', flag: '🇹🇷' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  const currentLang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
  const current = LANGUAGES.find((l) => l.code === currentLang) ?? LANGUAGES[0];

  // Dışarı tıklayınca kapat
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  // Escape tuşuyla kapat
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open]);

  const handleSelect = (code) => {
    i18n.changeLanguage(code);
    setOpen(false);
    // Backend'deki kullanıcı kaydını da güncelle — bildirim ve mailler bu değeri kullanır
    api.put('/users/me/language', null, { params: { lang: code } }).catch((err) => {
      console.warn('Dil tercihi backend\'e kaydedilemedi:', err);
    });
  };

  return (
    <div ref={containerRef} className="relative">
      {/* Trigger button */}
      <button
        onClick={() => setOpen((prev) => !prev)}
        className="flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-xs font-semibold transition-all duration-200 cursor-pointer select-none"
        style={{
          backgroundColor: 'var(--bg-surface-secondary)',
          color: 'var(--text-secondary)',
        }}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="Select language"
      >
        <span className="text-sm leading-none">{current.flag}</span>
        <span className="hidden sm:inline">{current.label}</span>
        <ChevronDown
          className="h-3.5 w-3.5 transition-transform duration-200"
          style={{ transform: open ? 'rotate(180deg)' : 'rotate(0deg)' }}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          className="absolute right-0 top-full mt-1.5 w-36 rounded-xl border overflow-hidden z-50"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-xl, 0 10px 40px rgba(0,0,0,0.15))',
          }}
          role="listbox"
          aria-label="Language options"
        >
          {LANGUAGES.map((lang) => {
            const isSelected = lang.code === currentLang;
            return (
              <button
                key={lang.code}
                role="option"
                aria-selected={isSelected}
                onClick={() => handleSelect(lang.code)}
                className="flex w-full items-center gap-2.5 px-3 py-2.5 text-sm transition-colors cursor-pointer"
                style={{
                  backgroundColor: isSelected ? 'var(--bg-surface-secondary)' : 'transparent',
                  color: isSelected ? 'var(--text-primary)' : 'var(--text-secondary)',
                }}
                onMouseEnter={(e) => {
                  if (!isSelected) e.currentTarget.style.backgroundColor = 'var(--bg-surface-secondary)';
                }}
                onMouseLeave={(e) => {
                  if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                <span className="text-base leading-none">{lang.flag}</span>
                <span className="flex-1 text-left font-medium">{lang.label}</span>
                {isSelected && (
                  <Check className="h-3.5 w-3.5 flex-shrink-0" style={{ color: '#3b82f6' }} />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
