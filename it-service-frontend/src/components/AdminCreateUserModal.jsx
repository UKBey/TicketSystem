import { useState, useEffect } from 'react';
import { X, UserPlus, Loader2, AlertTriangle, Eye, EyeOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { createUser, getAssignableRoles } from '../services/api';
import { useEscapeToClose } from '../hooks/useEscapeToClose';

/**
 * AdminCreateUserModal — AGENT_ADMIN'in Keycloak'ta yeni kullanıcı oluşturması için modal.
 *
 * Props:
 *   isOpen         {boolean}  — Modal açık mı?
 *   onClose        {function} — Modal kapatma callback'i
 *   onUserCreated  {function} — Başarılı oluşturma sonrası çağrılır: (newUser) => {}
 */
export default function AdminCreateUserModal({ isOpen, onClose, onUserCreated }) {
  const { t } = useTranslation();

  const initialForm = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    roles: [],
  };

  const [formData, setFormData] = useState(initialForm);
  const [errors, setErrors]     = useState({});
  const [serverError, setServerError] = useState(null); // { field?, message }
  const [loading, setLoading]   = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [availableRoles, setAvailableRoles] = useState([]);
  const [rolesLoading, setRolesLoading]     = useState(false);
  const [rolesError, setRolesError]         = useState('');

  // Modal açıldığında state'i sıfırla ve rolleri çek
  useEffect(() => {
    if (!isOpen) return;

    setFormData(initialForm);
    setErrors({});
    setServerError(null);
    setShowPassword(false);
    setRolesError('');

    setRolesLoading(true);
    getAssignableRoles()
      .then((res) => setAvailableRoles(res.data))
      .catch(() => setRolesError(t('userManagement.form.rolesLoadError')))
      .finally(() => setRolesLoading(false));
  }, [isOpen]); // eslint-disable-line

  // ESC ile kapatma
  useEscapeToClose(isOpen, onClose);

  // -------------------------------------------------------------------------
  // Validasyon
  // -------------------------------------------------------------------------
  const validate = () => {
    const newErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const passwordRegex = /^(?=.*[A-Z])(?=.*\d).{8,}$/;

    if (!formData.username.trim()) {
      newErrors.username = t('userManagement.validation.usernameRequired');
    } else if (formData.username.trim().length < 3) {
      newErrors.username = t('userManagement.validation.usernameMinLength');
    }

    if (!formData.email.trim()) {
      newErrors.email = t('userManagement.validation.emailRequired');
    } else if (!emailRegex.test(formData.email.trim())) {
      newErrors.email = t('userManagement.validation.emailInvalid');
    }

    if (!formData.firstName.trim()) {
      newErrors.firstName = t('userManagement.validation.firstNameRequired');
    }

    if (!formData.lastName.trim()) {
      newErrors.lastName = t('userManagement.validation.lastNameRequired');
    }

    if (!formData.password) {
      newErrors.password = t('userManagement.validation.passwordRequired');
    } else if (!passwordRegex.test(formData.password)) {
      newErrors.password = t('userManagement.validation.passwordWeak');
    }

    if (formData.roles.length === 0) {
      newErrors.roles = t('userManagement.validation.rolesRequired');
    }

    return newErrors;
  };

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    // Alan değişince o alanın hatasını ve server hatasını temizle
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: '' }));
    if (serverError?.field === name) setServerError(null);
  };

  const handleRoleToggle = (roleName) => {
    setFormData((prev) => {
      const exists = prev.roles.includes(roleName);
      return {
        ...prev,
        roles: exists
          ? prev.roles.filter((r) => r !== roleName)
          : [...prev.roles, roleName],
      };
    });
    if (errors.roles) setErrors((prev) => ({ ...prev, roles: '' }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError(null);

    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setLoading(true);
    try {
      const payload = {
        username:  formData.username.trim(),
        email:     formData.email.trim(),
        firstName: formData.firstName.trim(),
        lastName:  formData.lastName.trim(),
        password:  formData.password,
        roles:     formData.roles,
      };

      const res = await createUser(payload);
      onUserCreated(res.data);
      onClose();
    } catch (err) {
      const status = err.response?.status;
      const data   = err.response?.data;

      if (status === 409 && data?.fieldErrors) {
        // Alan bazlı çakışma — ilgili input altında göster
        const conflictField = Object.keys(data.fieldErrors)[0];
        const conflictMsg   = Object.values(data.fieldErrors)[0];
        setErrors((prev) => ({ ...prev, [conflictField]: conflictMsg }));
      } else if (status === 400 && data?.fieldErrors) {
        // Bean Validation hatası
        setErrors(data.fieldErrors);
      } else {
        // Genel hata banner'ı
        setServerError({
          message: data?.message || t('userManagement.form.errorGeneral'),
        });
      }
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  // -------------------------------------------------------------------------
  // Render yardımcıları
  // -------------------------------------------------------------------------
  const inputClass = (field) =>
    `w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 ${
      errors[field] ? 'border-red-400' : ''
    }`;

  const inputStyle = (field) => ({
    backgroundColor: 'var(--bg-input)',
    borderColor: errors[field] ? '#f87171' : 'var(--border-color)',
    color: 'var(--text-primary)',
    '--tw-ring-color': errors[field] ? 'rgba(248,113,113,0.3)' : 'var(--ring-color)',
  });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
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
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-500/10">
              <UserPlus className="h-5 w-5 text-primary-500" />
            </div>
            <div>
              <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.form.title')}
              </h3>
              <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                {t('userManagement.form.subtitle')}
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

            {/* Genel sunucu hatası */}
            {serverError && !serverError.field && (
              <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                {serverError.message}
              </div>
            )}

            {/* Ad — Soyad (mobilde alt alta, sm ve uzeri yan yana) */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  {t('userManagement.form.firstName')} *
                </label>
                <input
                  type="text"
                  name="firstName"
                  value={formData.firstName}
                  onChange={handleChange}
                  autoComplete="given-name"
                  className={inputClass('firstName')}
                  style={inputStyle('firstName')}
                  placeholder="John"
                />
                {errors.firstName && (
                  <p className="mt-1 text-xs text-red-400">{errors.firstName}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  {t('userManagement.form.lastName')} *
                </label>
                <input
                  type="text"
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleChange}
                  autoComplete="family-name"
                  className={inputClass('lastName')}
                  style={inputStyle('lastName')}
                  placeholder="Doe"
                />
                {errors.lastName && (
                  <p className="mt-1 text-xs text-red-400">{errors.lastName}</p>
                )}
              </div>
            </div>

            {/* Kullanıcı adı */}
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.form.username')} *
              </label>
              <input
                type="text"
                name="username"
                value={formData.username}
                onChange={handleChange}
                autoComplete="username"
                className={inputClass('username')}
                style={inputStyle('username')}
                placeholder="john.doe"
              />
              {errors.username && (
                <p className="mt-1 text-xs text-red-400">{errors.username}</p>
              )}
            </div>

            {/* E-posta */}
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.form.email')} *
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                autoComplete="email"
                className={inputClass('email')}
                style={inputStyle('email')}
                placeholder="john.doe@example.com"
              />
              {errors.email && (
                <p className="mt-1 text-xs text-red-400">{errors.email}</p>
              )}
            </div>

            {/* Şifre */}
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.form.password')} *
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  autoComplete="new-password"
                  className={inputClass('password')}
                  style={{ ...inputStyle('password'), paddingRight: '2.5rem' }}
                  placeholder="Min. 8 karakter, 1 büyük harf, 1 rakam"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 cursor-pointer"
                  style={{ color: 'var(--text-tertiary)' }}
                  tabIndex={-1}
                  aria-label={showPassword ? 'Şifreyi gizle' : 'Şifreyi göster'}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password ? (
                <p className="mt-1 text-xs text-red-400">{errors.password}</p>
              ) : (
                <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                  {t('userManagement.form.passwordHint')}
                </p>
              )}
            </div>

            {/* Rol seçimi */}
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                {t('userManagement.form.roles')} *
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
                    const isChecked = formData.roles.includes(role);
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
                          color: isChecked
                            ? '#6366f1'
                            : 'var(--text-secondary)',
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

              {errors.roles && (
                <p className="mt-1.5 text-xs text-red-400">{errors.roles}</p>
              )}
              <p className="mt-1.5 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {t('userManagement.form.rolesHint')}
              </p>
            </div>

            {/* Geçici şifre uyarısı */}
            <div
              className="rounded-lg px-3 py-2.5 text-xs"
              style={{
                backgroundColor: 'rgba(245,158,11,0.08)',
                borderLeft: '3px solid #f59e0b',
                color: 'var(--text-secondary)',
              }}
            >
              {t('userManagement.form.tempPasswordNote')}
            </div>
          </div>

          {/* Footer */}
          <div
            className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-6 py-4 border-t flex-shrink-0"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
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
              disabled={loading}
              className="inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors focus:outline-none focus:ring-4 focus:ring-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer w-full sm:w-auto"
            >
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('userManagement.form.submitting')}
                </>
              ) : (
                <>
                  <UserPlus className="h-4 w-4" />
                  {t('userManagement.form.submit')}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
