import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Headset, Mail, Moon, Sun } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import { requestPasswordReset } from '../services/authApi';

export default function ForgotPasswordPage() {
  const { t } = useTranslation();
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    if (!email.trim() || submitting) return;
    setError('');
    setSubmitting(true);
    try {
      await requestPasswordReset(email.trim());
      setSubmitted(true);
    } catch (requestError) {
      if (requestError.response?.status === 429) {
        setError(t('forgotPassword.rateLimit'));
      } else if (requestError.response?.status === 400) {
        setError(t('forgotPassword.invalidEmail'));
      } else {
        setError(t('forgotPassword.unknownError'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="flex min-h-screen items-center justify-center p-4 relative overflow-hidden transition-colors duration-300"
      style={{
        background: isDark
          ? 'linear-gradient(135deg, #0f172a 0%, #1e293b 40%, #0f172a 70%, #1a1a2e 100%)'
          : 'linear-gradient(135deg, #eff6ff 0%, #f8fafc 40%, #f0f9ff 70%, #faf5ff 100%)',
      }}
    >
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className={`absolute -top-40 -right-40 h-96 w-96 rounded-full blur-3xl ${isDark ? 'bg-primary-500/10' : 'bg-primary-500/8'}`} />
        <div className={`absolute -bottom-40 -left-40 h-96 w-96 rounded-full blur-3xl ${isDark ? 'bg-violet-500/10' : 'bg-violet-500/6'}`} />
      </div>

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
        <div
          className="rounded-2xl border p-6 sm:p-10 backdrop-blur-xl transition-all duration-300"
          style={{
            backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.85)',
            borderColor:     isDark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)',
            boxShadow:       isDark ? '0 25px 50px -12px rgba(0,0,0,0.5)' : '0 25px 50px -12px rgba(0,0,0,0.10)',
          }}
        >
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-primary-500 to-primary-700 shadow-lg shadow-primary-500/25">
            <Headset className="h-7 w-7 text-white" />
          </div>

          <h1 className="text-2xl font-bold text-center mb-2" style={{ color: isDark ? '#ffffff' : '#0f172a' }}>
            {t('forgotPassword.title')}
          </h1>
          <p className="text-sm text-center mb-6 leading-relaxed" style={{ color: isDark ? '#94a3b8' : '#64748b' }}>
            {t('forgotPassword.subtitle')}
          </p>

          {submitted ? (
            <div
              className="rounded-xl border px-4 py-4 text-sm leading-relaxed"
              style={{
                backgroundColor: isDark ? 'rgba(34,197,94,0.10)' : 'rgba(34,197,94,0.08)',
                borderColor:     isDark ? 'rgba(34,197,94,0.30)' : 'rgba(34,197,94,0.25)',
                color:           isDark ? '#86efac' : '#15803d',
              }}
            >
              {t('forgotPassword.success')}
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <label className="block">
                <span className="block text-xs font-semibold uppercase tracking-wider mb-1.5" style={{ color: isDark ? '#94a3b8' : '#64748b' }}>
                  {t('forgotPassword.emailLabel')}
                </span>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4" style={{ color: isDark ? '#64748b' : '#94a3b8' }} />
                  <input
                    type="email"
                    autoComplete="email"
                    required
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder={t('forgotPassword.emailPlaceholder')}
                    className="w-full rounded-xl border pl-10 pr-3 py-3 text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500"
                    style={{
                      backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.9)',
                      borderColor:     isDark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.08)',
                      color:           isDark ? '#ffffff' : '#0f172a',
                    }}
                  />
                </div>
              </label>

              {error && (
                <div
                  className="rounded-lg border px-3 py-2 text-xs"
                  style={{
                    backgroundColor: isDark ? 'rgba(239,68,68,0.10)' : 'rgba(239,68,68,0.06)',
                    borderColor:     isDark ? 'rgba(239,68,68,0.30)' : 'rgba(239,68,68,0.25)',
                    color:           isDark ? '#fca5a5' : '#b91c1c',
                  }}
                >
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-500 px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-primary-600 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? t('forgotPassword.submitting') : t('forgotPassword.submit')}
              </button>
            </form>
          )}

          <Link
            to="/"
            className="mt-6 inline-flex items-center justify-center gap-1.5 text-xs font-semibold w-full"
            style={{ color: isDark ? '#94a3b8' : '#64748b' }}
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            {t('forgotPassword.backToLogin')}
          </Link>
        </div>
      </div>
    </div>
  );
}
