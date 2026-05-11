import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Headset, ArrowRight, Moon, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '../components/LanguageSwitcher';

export default function LoginPage() {
  const { login } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const { t } = useTranslation();
  const isDark = theme === 'dark';

  return (
    <div
      className="flex min-h-screen items-center justify-center p-4 relative overflow-hidden transition-colors duration-300"
      style={{
        background: isDark
          ? 'linear-gradient(135deg, #0f172a 0%, #1e293b 40%, #0f172a 70%, #1a1a2e 100%)'
          : 'linear-gradient(135deg, #eff6ff 0%, #f8fafc 40%, #f0f9ff 70%, #faf5ff 100%)',
      }}
    >
      {/* Background decorative orbs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className={`absolute -top-40 -right-40 h-96 w-96 rounded-full blur-3xl ${isDark ? 'bg-primary-500/10' : 'bg-primary-500/8'}`} />
        <div className={`absolute -bottom-40 -left-40 h-96 w-96 rounded-full blur-3xl ${isDark ? 'bg-violet-500/10' : 'bg-violet-500/6'}`} />
        <div className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[600px] w-[600px] rounded-full blur-3xl ${isDark ? 'bg-primary-500/5' : 'bg-primary-500/4'}`} />
      </div>

      {/* Language switcher + theme toggle — top right */}
      <div className="fixed top-4 right-4 z-50 flex items-center gap-2">
        <LanguageSwitcher />
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={t('login.toggleTheme')}
          className="flex h-10 w-10 items-center justify-center rounded-xl border backdrop-blur-sm transition-all duration-200 hover:scale-105"
          style={{
            backgroundColor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)',
            borderColor:     isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.10)',
            color:           isDark ? '#94a3b8' : '#64748b',
          }}
        >
          {isDark ? <Sun className="h-4.5 w-4.5" /> : <Moon className="h-4.5 w-4.5" />}
        </button>
      </div>

      <div className="relative w-full max-w-md animate-slide-up">
        {/* Main card */}
        <div
          className="rounded-2xl border p-10 text-center backdrop-blur-xl transition-all duration-300"
          style={{
            backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.85)',
            borderColor:     isDark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)',
            boxShadow:       isDark
              ? '0 25px 50px -12px rgba(0,0,0,0.5)'
              : '0 25px 50px -12px rgba(0,0,0,0.10)',
          }}
        >
          {/* Logo */}
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-500 to-primary-700 shadow-lg shadow-primary-500/25">
            <Headset className="h-8 w-8 text-white" />
          </div>

          <h1
            className="text-3xl font-bold mb-2 transition-colors duration-300"
            style={{ color: isDark ? '#ffffff' : '#0f172a' }}
          >
            IT Service Desk
          </h1>
          <p
            className="text-sm mb-8 leading-relaxed transition-colors duration-300"
            style={{ color: isDark ? '#94a3b8' : '#64748b' }}
          >
            {t('login.subtitle')}
          </p>

          <button
            onClick={login}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-500 px-6 py-3.5 text-sm font-semibold text-white transition-all duration-200 hover:bg-primary-600 hover:scale-[1.02] hover:shadow-lg hover:shadow-primary-500/25 active:scale-[0.98] cursor-pointer"
          >
            {t('login.signIn')}
            <ArrowRight className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
