import { useState, useEffect } from 'react';
import { X, ShieldCheck, Loader2, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getAssignableRoles, updateUserRoles } from '../services/api';
import { useEscapeToClose } from '../hooks/useEscapeToClose';

/**
 * EditRoleModal — ADMIN'in mevcut bir kullanıcının rollerini düzenlemesi için modal.
 * Çoklu rol seçimi destekler; mevcut roller açılışta ön-seçili gelir.
 *
 * Props:
 *   isOpen        {boolean}  — Modal açık mı?
 *   onClose       {function} — Modal kapatma callback'i
 *   user          {object}   — Düzenlenecek kullanıcı: { id, fullName, email, role | roles }
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
    // Mevcut rolleri başlangıç seçimi olarak ayarla (çoklu rol veya tekil rol destekli).
    const existing = Array.isArray(user.roles)
      ? user.roles
      : (user.role ? [user.role] : []);
    setSelectedRoles(existing);

    setRolesLoading(true);
    setRolesError('');
    getAssignableRoles()
      .then((res) => setAvailableRoles(res.data || []))
      .catch(() => setRolesError(t('userManagement.form.rolesLoadError')))
      .finally(() => setRolesLoading(false));
  }, [isOpen, user]); // eslint-disable-line

  // ESC ile kapatma
  useEscapeToClose(isOpen, onClose);

  const handleRoleToggle = (roleName) => {
    setSelectedRoles((prev) => {
      if (prev.includes(roleName)) return prev.filter((r) => r !== roleName);
      let next = [...prev, roleName];
      // customer tekil roldür: müşteri başka hiçbir rolle birlikte olamaz.
      if (roleName === 'CUSTOMER') return ['CUSTOMER'];
      next = next.filter((r) => r !== 'CUSTOMER');
      // agent ↔ lead_agent karşılıklı dışlama: lead zaten agent'ı kapsar.
      if (roleName === 'LEAD_AGENT') next = next.filter((r) => r !== 'AGENT');
      if (roleName === 'AGENT') next = next.filter((r) => r !== 'LEAD_AGENT');
      return next;
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
        className="w-full max-w-md sm:max-w-lg max-h-[90vh] flex flex-col rounded-xl border animate-slide-up"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-xl)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between gap-4 px-6 py-4 border-b flex-shrink-0"
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
              <p className="text-xs mt-0.5 truncate max-w-[180px] sm:max-w-[280px]" style={{ color: 'var(--text-secondary)' }}>
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

        <form onSubmit={handleSubmit} noValidate className="flex-1 flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto px-6 py-5 space-y-4">

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
            className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-6 py-4 border-t flex-shrink-0"
            style={{ borderColor: 'var(--border-color)' }}
          >
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-50 w-full sm:w-auto"
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
              className="inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors focus:outline-none focus:ring-4 focus:ring-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer w-full sm:w-auto"
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
