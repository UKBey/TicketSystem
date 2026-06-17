import { useNavigate } from 'react-router-dom';
import { Sun, Moon, Menu, Search } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import { useAuth } from '../context/AuthContext';
import { useCommandPalette } from '../context/CommandPaletteContext';
import { useTranslation } from 'react-i18next';
import { MOD_KEY_LABEL } from '../utils/platform';
import NotificationBell from './notifications/NotificationBell';
import LanguageSwitcher from './LanguageSwitcher';

export default function Navbar({ onMenuClick }) {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const { user, getPrimaryRole } = useAuth();
  const { openPalette } = useCommandPalette();
  const { t } = useTranslation();
  const primaryRole = getPrimaryRole();

  // Dairesel reveal animasyonu butonun merkezinden yayilsin (fare + klavye).
  const handleThemeToggle = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    toggleTheme({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 });
  };

  const initials = (user?.name || user?.username || 'U')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <header
      className="sticky top-0 z-30 flex h-16 items-center justify-between border-b px-3 sm:px-6 backdrop-blur-xl gap-2"
      style={{
        backgroundColor: 'color-mix(in srgb, var(--bg-surface) 85%, transparent)',
        borderColor: 'var(--border-color)',
      }}
    >
      {/* Mobile hamburger — sadece md altinda gorunur. md ve uzeri sidebar her zaman ekranda. */}
      <button
        type="button"
        onClick={onMenuClick}
        className="md:hidden flex h-9 w-9 items-center justify-center rounded-lg transition-all duration-200 cursor-pointer"
        style={{
          backgroundColor: 'var(--bg-surface-secondary)',
          color: 'var(--text-secondary)',
        }}
        aria-label={t('sidebar.expand')}
      >
        <Menu className="h-5 w-5" />
      </button>

      {/* Command palette tetikleyici — masaüstünde arama-çubuğu görünümü + OS'e göre
          kısayol ipucu (⌘K / Ctrl K); mobilde yalnızca ikon. */}
      <button
        type="button"
        onClick={openPalette}
        title={t('commandPalette.open')}
        aria-label={t('commandPalette.open')}
        data-tour="cmd-palette"
        className="flex items-center gap-2 rounded-lg transition-all duration-200 cursor-pointer h-9 px-2.5 sm:w-64 sm:justify-start hover:opacity-90"
        style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }}
      >
        <Search className="h-4 w-4 flex-shrink-0" />
        <span className="hidden sm:block flex-1 text-left text-sm truncate">{t('commandPalette.searchPlaceholder')}</span>
        <span className="hidden sm:flex items-center gap-1">
          <kbd
            className="inline-flex h-5 min-w-[20px] items-center justify-center rounded border px-1 font-sans text-[11px] font-semibold leading-none"
            style={{ backgroundColor: 'var(--kbd-bg)', borderColor: 'var(--kbd-border)', color: 'var(--kbd-text)', boxShadow: 'var(--shadow-sm)' }}
          >
            {MOD_KEY_LABEL}
          </kbd>
          <kbd
            className="inline-flex h-5 min-w-[20px] items-center justify-center rounded border px-1 font-sans text-[11px] font-semibold leading-none"
            style={{ backgroundColor: 'var(--kbd-bg)', borderColor: 'var(--kbd-border)', color: 'var(--kbd-text)', boxShadow: 'var(--shadow-sm)' }}
          >
            K
          </kbd>
        </span>
      </button>

      {/* Right: Actions */}
      <div className="flex items-center gap-1.5 sm:gap-2 ml-auto min-w-0">
        {/* Language switcher */}
        <span data-tour="lang-switch" className="inline-flex items-center">
          <LanguageSwitcher />
        </span>

        {/* Theme toggle — ikonlar donerek/olceklenerek gecis yapar; tema
            degisiminin kendisi ThemeContext'te dairesel reveal ile animasyonlu. */}
        <button
          onClick={handleThemeToggle}
          data-tour="theme-toggle"
          className="relative flex h-9 w-9 items-center justify-center overflow-hidden rounded-lg transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer"
          style={{
            backgroundColor: 'var(--bg-surface-secondary)',
            color: 'var(--text-secondary)',
          }}
          aria-label={theme === 'light' ? t('nav.theme.toDark') : t('nav.theme.toLight')}
          title={theme === 'light' ? t('nav.theme.toDark') : t('nav.theme.toLight')}
        >
          <span className="relative h-[18px] w-[18px]">
            {/* Light temadayken Moon (karanliga gec); dark'tayken Sun (aydinliga gec). */}
            <Moon
              className={`absolute inset-0 h-[18px] w-[18px] transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] ${
                theme === 'light'
                  ? 'rotate-0 scale-100 opacity-100'
                  : '-rotate-90 scale-0 opacity-0'
              }`}
            />
            <Sun
              className={`absolute inset-0 h-[18px] w-[18px] transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] ${
                theme === 'dark'
                  ? 'rotate-0 scale-100 opacity-100'
                  : 'rotate-90 scale-0 opacity-0'
              }`}
            />
          </span>
        </button>

        {/* Notifications */}
        <span data-tour="notif-bell" className="inline-flex items-center">
          <NotificationBell />
        </span>

        {/* Divider */}
        <div className="hidden sm:block mx-2 h-8 w-px" style={{ backgroundColor: 'var(--border-color)' }} />

        {/* User info — clickable, navigates to profile */}
        <button
          onClick={() => navigate('/profile')}
          data-tour="profile-menu"
          className="flex items-center gap-2 sm:gap-3 rounded-lg px-1 sm:px-2 py-1.5 transition-colors cursor-pointer hover:opacity-80 min-w-0"
          style={{ background: 'none', border: 'none' }}
        >
          <div
            className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold text-white"
            style={{ background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)' }}
          >
            {initials}
          </div>
          <div className="hidden sm:block text-left min-w-0">
            <div className="text-sm font-semibold leading-tight truncate max-w-[140px]" style={{ color: 'var(--text-primary)' }}>
              {user?.name || user?.username || 'User'}
            </div>
            <div className="text-xs leading-tight truncate" style={{ color: 'var(--text-tertiary)' }}>
              {primaryRole || 'USER'}
            </div>
          </div>
        </button>
      </div>
    </header>
  );
}
