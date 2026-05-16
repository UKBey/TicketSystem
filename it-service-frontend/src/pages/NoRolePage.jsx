import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ShieldAlert } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '../components/LanguageSwitcher';

export default function NoRolePage() {
  const { user, logout, getPrimaryRole } = useAuth();
  const { t } = useTranslation();

  if (getPrimaryRole() !== null) {
    return <Navigate to="/" replace />;
  }

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center p-4 sm:p-6"
      style={{ backgroundColor: 'var(--bg-body)' }}
    >
      <div className="fixed top-4 right-4 z-50">
        <LanguageSwitcher />
      </div>

      <div className="w-full max-w-lg">
        <div
          className="rounded-2xl border p-6 sm:p-8 text-center"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-xl)',
          }}
        >
          <div
            className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl"
            style={{ backgroundColor: '#fef3c7' }}
          >
            <ShieldAlert className="h-8 w-8" style={{ color: '#d97706' }} />
          </div>

          <h1 className="text-xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
            {t('noRole.title')}
          </h1>
          <p className="text-sm mb-6 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            {t('noRole.description')}
          </p>

          {user && (
            <div
              className="rounded-lg px-4 py-3 mb-6 text-left text-sm"
              style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}
            >
              <div className="font-medium mb-0.5" style={{ color: 'var(--text-primary)' }}>
                {user.name || user.username}
              </div>
              <div style={{ color: 'var(--text-tertiary)' }}>{user.email}</div>
            </div>
          )}

          <button
            onClick={logout}
            className="w-full rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
          >
            {t('noRole.signOut')}
          </button>
        </div>
      </div>
    </div>
  );
}
