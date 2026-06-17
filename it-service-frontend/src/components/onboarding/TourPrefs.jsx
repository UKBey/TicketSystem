import { useTranslation } from 'react-i18next';
import { Sun, Moon, Check } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import api from '../../services/api';
import keycloak from '../../keycloak';

const LANGUAGES = [
  { code: 'en', label: 'English', flag: '🇺🇸' },
  { code: 'tr', label: 'Türkçe', flag: '🇹🇷' },
];

/**
 * Karşılama adımında gösterilen dil + tema seçici. Kullanıcı tura başlamadan önce
 * portalı kendi tercihiyle gezsin diye en başta sunulur — seçimler anında uygulanır
 * ve (oturum açıksa) backend'e yazılır. Navbar'daki anahtarlarla aynı context'i kullanır.
 */
export default function TourPrefs() {
  const { t, i18n } = useTranslation();
  const { theme, setTheme } = useTheme();
  const currentLang = i18n.language?.startsWith('tr') ? 'tr' : 'en';

  const selectLang = (code) => {
    if (code === currentLang) return;
    i18n.changeLanguage(code);
    if (keycloak.authenticated && keycloak.token) {
      api.put('/users/me/language', null, { params: { lang: code } }).catch(() => {});
    }
  };

  const selectTheme = (value, e) => {
    if (value === theme) return;
    const rect = e.currentTarget.getBoundingClientRect();
    setTheme(value, { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 });
  };

  const segBase =
    'relative flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold transition-all duration-200 cursor-pointer';

  const segStyle = (active) =>
    active
      ? { background: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)', color: '#fff', boxShadow: '0 4px 12px rgba(99,102,241,0.35)' }
      : { backgroundColor: 'transparent', color: 'var(--text-secondary)' };

  const themeOptions = [
    { value: 'light', label: t('onboarding.prefs.light'), Icon: Sun },
    { value: 'dark', label: t('onboarding.prefs.dark'), Icon: Moon },
  ];

  return (
    <div className="mt-4 flex flex-col gap-3">
      {/* Tema (D/L) — üstte */}
      <div>
        <span
          className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wide"
          style={{ color: 'var(--text-tertiary)' }}
        >
          {t('onboarding.prefs.themeLabel')}
        </span>
        <div
          className="flex gap-1 rounded-xl p-1"
          style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
        >
          {themeOptions.map(({ value, label, Icon }) => {
            const active = value === theme;
            return (
              <button
                key={value}
                type="button"
                onClick={(e) => selectTheme(value, e)}
                className={segBase}
                style={segStyle(active)}
                aria-pressed={active}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Dil — altta */}
      <div>
        <span
          className="mb-1.5 block text-[11px] font-semibold uppercase tracking-wide"
          style={{ color: 'var(--text-tertiary)' }}
        >
          {t('onboarding.prefs.langLabel')}
        </span>
        <div
          className="flex gap-1 rounded-xl p-1"
          style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
        >
          {LANGUAGES.map((lang) => {
            const active = lang.code === currentLang;
            return (
              <button
                key={lang.code}
                type="button"
                onClick={() => selectLang(lang.code)}
                className={segBase}
                style={segStyle(active)}
                aria-pressed={active}
              >
                <span className="text-base leading-none">{lang.flag}</span>
                {lang.label}
                {active && <Check className="h-3.5 w-3.5" />}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
