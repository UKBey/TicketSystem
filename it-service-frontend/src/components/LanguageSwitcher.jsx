import { useTranslation } from 'react-i18next';

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const currentLang = i18n.language?.startsWith('tr') ? 'tr' : 'en';

  const toggle = () => {
    const next = currentLang === 'en' ? 'tr' : 'en';
    i18n.changeLanguage(next);
  };

  return (
    <button
      onClick={toggle}
      className="relative flex h-9 w-9 items-center justify-center rounded-lg transition-all duration-200 hover:scale-105 cursor-pointer text-xs font-bold"
      style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}
      aria-label={currentLang === 'en' ? 'Switch to Turkish' : 'Switch to English'}
      title={currentLang === 'en' ? 'TR' : 'EN'}
    >
      {currentLang === 'en' ? 'TR' : 'EN'}
    </button>
  );
}
