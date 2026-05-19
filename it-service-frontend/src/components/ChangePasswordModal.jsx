import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Eye, EyeOff, Lock, Check } from 'lucide-react';
import userService from '../services/userService';
import { useEscapeToClose } from '../hooks/useEscapeToClose';

const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d).{8,}$/;

function PasswordInput({ id, value, onChange, placeholder, disabled, error, autoFocus }) {
  const [visible, setVisible] = useState(false);
  // F-4: error varken aria-invalid="true" ve aria-describedby ile hata mesajini
  // screen reader'a baglar. Mesaj <p id="{id}-error" role="alert"> ile DOM'da.
  return (
    <div className="relative">
      <input
        id={id}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        autoComplete="new-password"
        aria-invalid={!!error}
        aria-describedby={error ? `${id}-error` : undefined}
        className="w-full rounded-md border px-3 py-2 pr-10 text-sm font-medium outline-none disabled:opacity-60"
        style={{
          backgroundColor: 'var(--bg-surface-secondary)',
          borderColor: error ? '#ef4444' : 'var(--border-color)',
          color: 'var(--text-primary)',
        }}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        tabIndex={-1}
        className="absolute right-2 top-1/2 -translate-y-1/2 flex h-7 w-7 items-center justify-center rounded-md"
        style={{ color: 'var(--text-tertiary)' }}
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
}

export default function ChangePasswordModal({ open, onClose, onSuccess }) {
  const { t } = useTranslation();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword,     setNewPassword]     = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting,   setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState('');
  const [success,      setSuccess]    = useState(false);

  // Reset form whenever the modal is re-opened.
  useEffect(() => {
    if (open) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setFieldErrors({});
      setGeneralError('');
      setSuccess(false);
    }
  }, [open]);

  // ESC to close.
  useEscapeToClose(open, onClose, { disabled: submitting });

  if (!open) return null;

  const validate = () => {
    const errs = {};
    if (!currentPassword) errs.currentPassword = t('profile.passwordModal.currentRequired');
    if (!newPassword)     errs.newPassword     = t('profile.passwordModal.newRequired');
    else if (!PASSWORD_REGEX.test(newPassword)) errs.newPassword = t('profile.passwordModal.weakPassword');
    if (newPassword !== confirmPassword) errs.confirmPassword = t('profile.passwordModal.mismatch');
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      setGeneralError('');
      return;
    }
    setFieldErrors({});
    setGeneralError('');
    setSubmitting(true);
    try {
      await userService.changePassword({ currentPassword, newPassword });
      setSuccess(true);
      // Show success briefly, then close.
      setTimeout(() => {
        onSuccess?.();
        onClose();
      }, 1200);
    } catch (err) {
      const data = err?.response?.data;
      const code = data?.error;
      if (code === 'WRONG_CURRENT_PASSWORD') {
        setFieldErrors({ currentPassword: t('profile.passwordModal.wrongCurrent') });
      } else if (code === 'INVALID_PASSWORD') {
        setFieldErrors({ newPassword: t('profile.passwordModal.policyViolation') });
      } else if (data?.fieldErrors) {
        setFieldErrors(data.fieldErrors);
      } else {
        setGeneralError(t('profile.passwordModal.genericError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget && !submitting) onClose(); }}
    >
      <div
        className="w-full max-w-md sm:max-w-lg max-h-[90vh] flex flex-col rounded-2xl border shadow-xl"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
      >
        {/* Header */}
        <div
          className="flex items-center gap-3 px-5 py-4 border-b flex-shrink-0"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <div
            className="flex h-9 w-9 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'rgba(245,158,11,0.12)' }}
          >
            <Lock className="h-4 w-4" style={{ color: '#f59e0b' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
              {t('profile.passwordModal.title')}
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              {t('profile.passwordModal.subtitle')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="flex h-8 w-8 items-center justify-center rounded-md disabled:opacity-50"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label="close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSubmit} className="flex-1 flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          <div>
            <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
              {t('profile.passwordModal.currentPassword')}
            </label>
            <PasswordInput
              id="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              placeholder={t('profile.passwordModal.currentPassword')}
              disabled={submitting || success}
              error={fieldErrors.currentPassword}
              autoFocus
            />
            {fieldErrors.currentPassword && (
              <p id="current-password-error" role="alert" className="mt-1 text-xs font-medium" style={{ color: '#ef4444' }}>
                {fieldErrors.currentPassword}
              </p>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
              {t('profile.passwordModal.newPassword')}
            </label>
            <PasswordInput
              id="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder={t('profile.passwordModal.newPassword')}
              disabled={submitting || success}
              error={fieldErrors.newPassword}
            />
            {fieldErrors.newPassword ? (
              <p id="new-password-error" role="alert" className="mt-1 text-xs font-medium" style={{ color: '#ef4444' }}>
                {fieldErrors.newPassword}
              </p>
            ) : (
              <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {t('profile.passwordModal.passwordHint')}
              </p>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium mb-1.5" style={{ color: 'var(--text-secondary)' }}>
              {t('profile.passwordModal.confirmPassword')}
            </label>
            <PasswordInput
              id="confirm-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder={t('profile.passwordModal.confirmPassword')}
              disabled={submitting || success}
              error={fieldErrors.confirmPassword}
            />
            {fieldErrors.confirmPassword && (
              <p id="confirm-password-error" role="alert" className="mt-1 text-xs font-medium" style={{ color: '#ef4444' }}>
                {fieldErrors.confirmPassword}
              </p>
            )}
          </div>

          {generalError && (
            <div
              className="rounded-md border px-3 py-2 text-xs font-medium"
              style={{ backgroundColor: 'rgba(239,68,68,0.08)', borderColor: 'rgba(239,68,68,0.3)', color: '#ef4444' }}
            >
              {generalError}
            </div>
          )}

          {success && (
            <div
              className="flex items-center gap-2 rounded-md border px-3 py-2 text-xs font-medium"
              style={{ backgroundColor: 'rgba(34,197,94,0.08)', borderColor: 'rgba(34,197,94,0.3)', color: '#22c55e' }}
            >
              <Check className="h-3.5 w-3.5" />
              {t('profile.passwordModal.success')}
            </div>
          )}

          </div>
          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 sm:gap-3 px-5 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="rounded-md border px-3 py-2 text-sm font-semibold transition-colors disabled:opacity-50 w-full sm:w-auto"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
            >
              {t('profile.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting || success}
              className="inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-white transition-colors disabled:cursor-not-allowed disabled:opacity-60 w-full sm:w-auto"
              style={{ backgroundColor: '#f59e0b' }}
            >
              <Lock className="h-3.5 w-3.5" />
              {submitting ? t('profile.passwordModal.submitting') : t('profile.passwordModal.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
