import { useRef, useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, Check } from 'lucide-react';
import api from '../services/api';
import keycloak from '../keycloak';

const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'tr', label: 'Türkçe' },
];

// Inline SVG flags — emoji flags (🇺🇸/🇹🇷) don't render on Windows/Chrome
// (they fall back to "US"/"TR" letters), so we draw them as SVG for a
// consistent look across every OS/browser. The Keycloak login theme mirrors
// these same flags (keycloak-themes/.../login/template.ftl).
function Flag({ code, className = 'h-3.5 w-5 shrink-0 rounded-[3px]' }) {
  if (code === 'tr') {
    return (
      <svg className={className} viewBox="0 0 20 14" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <rect width="20" height="14" fill="#E30A17" />
        <circle cx="7.6" cy="7" r="3" fill="#fff" />
        <circle cx="8.8" cy="7" r="2.45" fill="#E30A17" />
        <path fill="#fff" d="M12.3 5l.47 1.353 1.432.029-1.141.865.415 1.371L12.3 7.8l-1.176.818.415-1.371-1.141-.865 1.432-.029z" />
      </svg>
    );
  }
  return (
    <svg className={className} viewBox="0 0 20 14" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect width="20" height="14" fill="#fff" />
      <g fill="#B22234">
        <rect y="0" width="20" height="1.077" />
        <rect y="2.154" width="20" height="1.077" />
        <rect y="4.308" width="20" height="1.077" />
        <rect y="6.462" width="20" height="1.077" />
        <rect y="8.615" width="20" height="1.077" />
        <rect y="10.769" width="20" height="1.077" />
        <rect y="12.923" width="20" height="1.077" />
      </g>
      <rect width="8" height="7.54" fill="#3C3B6E" />
      <g fill="#fff">
        <circle cx="1.3" cy="1.2" r="0.35" /><circle cx="2.9" cy="1.2" r="0.35" /><circle cx="4.5" cy="1.2" r="0.35" /><circle cx="6.1" cy="1.2" r="0.35" />
        <circle cx="2.1" cy="2.5" r="0.35" /><circle cx="3.7" cy="2.5" r="0.35" /><circle cx="5.3" cy="2.5" r="0.35" />
        <circle cx="1.3" cy="3.8" r="0.35" /><circle cx="2.9" cy="3.8" r="0.35" /><circle cx="4.5" cy="3.8" r="0.35" /><circle cx="6.1" cy="3.8" r="0.35" />
        <circle cx="2.1" cy="5.1" r="0.35" /><circle cx="3.7" cy="5.1" r="0.35" /><circle cx="5.3" cy="5.1" r="0.35" />
        <circle cx="1.3" cy="6.4" r="0.35" /><circle cx="2.9" cy="6.4" r="0.35" /><circle cx="4.5" cy="6.4" r="0.35" /><circle cx="6.1" cy="6.4" r="0.35" />
      </g>
    </svg>
  );
}

export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
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
    // Backend'deki kullanıcı kaydını da güncelle — bildirim ve mailler bu değeri kullanır.
    // Login sayfasında token yok; bu durumda istek 401 doneceginden sunucuya yazmiyoruz.
    if (keycloak.authenticated && keycloak.token) {
      api.put('/users/me/language', null, { params: { lang: code } }).catch((err) => {
        console.warn('Dil tercihi backend\'e kaydedilemedi:', err);
      });
    }
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
        aria-label={t('nav.language.select')}
      >
        <Flag code={current.code} />
        <span className="hidden sm:inline">{current.label}</span>
        <ChevronDown
          className="h-3.5 w-3.5 transition-transform duration-200"
          style={{ transform: open ? 'rotate(180deg)' : 'rotate(0deg)' }}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          className="absolute right-0 top-full mt-1.5 w-36 max-w-[calc(100vw-1rem)] rounded-xl border overflow-hidden z-50"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-xl, 0 10px 40px rgba(0,0,0,0.15))',
          }}
          role="listbox"
          aria-label={t('nav.language.options')}
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
                <Flag code={lang.code} />
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
