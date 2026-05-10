import { useNavigate } from 'react-router-dom';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import { useAuth } from '../context/AuthContext';
import NotificationBell from './notifications/NotificationBell';
import LanguageSwitcher from './LanguageSwitcher';

export default function Navbar() {
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const { user, getPrimaryRole } = useAuth();
  const primaryRole = getPrimaryRole();
  const initials = (user?.name || user?.username || 'U')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <header
      className="sticky top-0 z-30 flex h-16 items-center justify-end border-b px-6 backdrop-blur-xl"
      style={{
        backgroundColor: 'color-mix(in srgb, var(--bg-surface) 85%, transparent)',
        borderColor: 'var(--border-color)',
      }}
    >
      {/* Right: Actions */}
      <div className="flex items-center gap-2">
        {/* Language switcher */}
        <LanguageSwitcher />

        {/* Theme toggle */}
        <button
          onClick={toggleTheme}
          className="relative flex h-9 w-9 items-center justify-center rounded-lg transition-all duration-200 hover:scale-105 cursor-pointer"
          style={{
            backgroundColor: 'var(--bg-surface-secondary)',
            color: 'var(--text-secondary)',
          }}
          aria-label={theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
          title={theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
        >
          {theme === 'light' ? (
            <Moon className="h-[18px] w-[18px]" />
          ) : (
            <Sun className="h-[18px] w-[18px]" />
          )}
        </button>

        {/* Notifications */}
        <NotificationBell />

        {/* Divider */}
        <div className="mx-2 h-8 w-px" style={{ backgroundColor: 'var(--border-color)' }} />

        {/* User info — clickable, navigates to profile */}
        <button
          onClick={() => navigate('/profile')}
          className="flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors cursor-pointer hover:opacity-80"
          style={{ background: 'none', border: 'none' }}
        >
          <div
            className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold text-white"
            style={{ background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)' }}
          >
            {initials}
          </div>
          <div className="hidden sm:block text-left">
            <div className="text-sm font-semibold leading-tight" style={{ color: 'var(--text-primary)' }}>
              {user?.name || user?.username || 'User'}
            </div>
            <div className="text-xs leading-tight" style={{ color: 'var(--text-tertiary)' }}>
              {primaryRole || 'USER'}
            </div>
          </div>
        </button>
      </div>
    </header>
  );
}
