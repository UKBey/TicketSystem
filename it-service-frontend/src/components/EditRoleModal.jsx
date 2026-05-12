import { useState, useEffect } from 'react';
import { X, ShieldCheck, Loader2, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getAssignableRoles, updateUserRoles } from '../services/api';

/**
 * EditRoleModal — AGENT_ADMIN'in mevcut bir kullanıcının rollerini düzenlemesi için modal.
 *
 * Props:
 *   isOpen        {boolean}  — Modal açık mı?
 *   onClose       {function} — Modal kapatma callback'i
 *   user          {object}   — Düzenlenecek kullanıcı: { id, fullName, email, role }
 *   onRoleUpdated {function} — Başarılı güncelleme sonrası çağrılır: (updatedUser) => {}
 */
export default function EditRoleModal({ isOpen, onClose, user, onRoleUpdated }) {
  const { t } = useTranslation();

  const [selectedRoles, setSelectedRoles] = useState([]);
  const [availableRoles, setAvailableRoles] = useState([]);
  const [rolesLoading, setRolesLoading]     = useState(false);
  const [rolesError, setRolesError]         = useState('');
  const [loading, setLoading]               = useState(false);
  const [error, setError]                   = useState('');
  const [validationError, setValidationError] = useState('');

  // Modal açıldığında rolleri çek ve mevcut rolü seç
  useEffect(() => {
    if (!isOpen || !user) return;

    setError('');
    setValidationError('');
    // Mevcut rolü başlangıç seçimi olarak ayarla
    setSelectedRoles(user.role ? [user.role] : []);

    setRolesLoading(true);
    setRolesError('');
    getAssignableRoles()
      .then((res) => setAvailableRoles(res.data))
      .catch(() => setRolesError(t('userManagement.form.rolesLoadError')))
      .finally(() => setRolesLoading(false));
  }, [isOpen, user]); // eslint-disable-line

  // ESC ile kapatma
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const handleRoleToggle = (roleName) => {
    setSelectedRoles((prev) => {
      const exists = prev.includes(roleName);
      return exists ? prev.filter((r) => r !== roleName) : [...prev, roleName];
    });
    setValidationError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (selectedRoles.length === 0) {
      setValidationError(t('userManagement.validation.rolesRequired'));
      return;
    }

    setLoading(true);
    try {
      const res = await updateUserRoles(user.id, selectedRoles);
      onRoleUpdated(res.data);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message;
      setError(msg || t('userManagement.editRole.errorGeneral'));
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen || !user) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-xl border animate-slide-up"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-xl)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between gap-4 px-6 py-4 border-b"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-500/10">
              <ShieldCheck className="h-5 w-5 text-primary-500" />
            </div>
            <div>
              <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.editRole.title')}
              </h3>
              <p className="text-xs mt-0.5 truncate max-w-[220px]" style={{ color: 'var(--text-secondary)' }}>
                {user.fullName} · {user.email}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('form.cancel')}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div className="px-6 py-5 space-y-4">

            {/* Genel hata */}
            {error && (
              <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* Rol seçimi */}
            <div>
              <label className="block text-sm font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.editRole.selectRoles')}
              </label>

              {rolesLoading ? (
                <div className="flex items-center gap-2 py-3" style={{ color: 'var(--text-tertiary)' }}>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span className="text-sm">{t('common.loading')}</span>
                </div>
              ) : rolesError ? (
                <p className="text-sm text-red-400">{rolesError}</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {availableRoles.map((role) => {
                    const isChecked = selectedRoles.includes(role);
                    return (
                      <button
                        key={role}
                        type="button"
                        onClick={() => handleRoleToggle(role)}
                        className="inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-semibold transition-all cursor-pointer"
                        style={{
                          backgroundColor: isChecked
                            ? 'rgba(99,102,241,0.12)'
                            : 'var(--bg-surface-secondary)',
                          borderColor: isChecked
                            ? 'rgba(99,102,241,0.5)'
                            : 'var(--border-color)',
                          color: isChecked ? '#6366f1' : 'var(--text-secondary)',
                        }}
                      >
                        {isChecked && (
                          <span className="h-1.5 w-1.5 rounded-full bg-indigo-500 inline-block" />
                        )}
                        {role}
                      </button>
                    );
                  })}
                </div>
              )}

              {validationError && (
                <p className="mt-1.5 text-xs text-red-400">{validationError}</p>
              )}
              <p className="mt-1.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {t('userManagement.form.rolesHint')}
              </p>
            </div>
          </div>

          {/* Footer */}
          <div
            className="flex justify-end gap-3 px-6 py-4 border-t"
            style={{ borderColor: 'var(--border-color)' }}
          >
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50"
              style={{
                borderColor: 'var(--border-color)',
                color: 'var(--text-secondary)',
                backgroundColor: 'transparent',
              }}
            >
              {t('form.cancel')}
            </button>
            <button
              type="submit"
              disabled={loading || rolesLoading}
              className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors focus:outline-none focus:ring-4 focus:ring-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('userManagement.editRole.saving')}
                </>
              ) : (
                <>
                  <ShieldCheck className="h-4 w-4" />
                  {t('userManagement.editRole.save')}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
