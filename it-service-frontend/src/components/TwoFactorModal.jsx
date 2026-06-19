import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { X, Smartphone, Plus, Trash2, ShieldCheck, AlertTriangle } from 'lucide-react';
import userService from '../services/userService';
import { useToast } from '../context/ToastContext';
import { redirectToKeycloakLogin } from '../keycloak';
import { useEscapeToClose } from '../hooks/useEscapeToClose';
import { formatDateTime } from '../utils/dateFormat';

// Tarih biçimi kullanıcının seçtiği formatta (utils/dateFormat); dil util içinden (i18n)
// okunur, bu yüzden lang param'ı (geriye dönük imza uyumu için durur) yok sayılır.
function formatDate(epochMillis, _lang) {
  return formatDateTime(epochMillis);
}

function DeviceRow({ device, lang, onDelete, deletingId, t }) {
  const [confirming, setConfirming] = useState(false);
  const isDeleting = deletingId === device.id;

  return (
    <div
      className="flex items-start gap-3 rounded-xl border px-4 py-3 transition-colors"
      style={{
        backgroundColor: 'var(--bg-surface-secondary)',
        borderColor: confirming ? 'rgba(239,68,68,0.4)' : 'var(--border-color)',
      }}
    >
      <div
        className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: 'rgba(16,185,129,0.12)' }}
      >
        <Smartphone className="h-4 w-4" style={{ color: '#10b981' }} />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold break-all" style={{ color: 'var(--text-primary)' }}>
          {device.userLabel || t('profile.twoFactorModal.deviceWithoutLabel')}
        </p>
        <p className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>
          {t('profile.twoFactorModal.createdOn', { date: formatDate(device.createdDate, lang) })}
        </p>

        {confirming && (
          <div className="mt-2 flex flex-col gap-2">
            <div className="flex items-center gap-2 text-xs" style={{ color: '#ef4444' }}>
              <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
              <span>{t('profile.twoFactorModal.confirmDeleteDesc')}</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onDelete(device.id)}
                disabled={isDeleting}
                className="inline-flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-semibold text-white transition-colors disabled:opacity-60"
                style={{ backgroundColor: '#ef4444' }}
              >
                <Trash2 className="h-3 w-3" />
                {isDeleting ? t('profile.twoFactorModal.deleting') : t('profile.twoFactorModal.deleteConfirm')}
              </button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                disabled={isDeleting}
                className="rounded-md border px-3 py-1 text-xs font-semibold transition-colors disabled:opacity-60"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              >
                {t('profile.twoFactorModal.deleteCancel')}
              </button>
            </div>
          </div>
        )}
      </div>

      {!confirming && (
        <button
          type="button"
          onClick={() => setConfirming(true)}
          className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md transition-colors hover:bg-[rgba(239,68,68,0.1)]"
          style={{ color: '#ef4444' }}
          aria-label={t('profile.twoFactorModal.delete')}
          title={t('profile.twoFactorModal.delete')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}

export default function TwoFactorModal({ open, onClose, lang }) {
  const { t } = useTranslation();
  const toast = useToast();

  const [devices, setDevices]   = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [deletingId, setDeletingId] = useState(null);

  const fetchDevices = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await userService.listTotpDevices();
      setDevices(Array.isArray(data) ? data : []);
    } catch {
      setError(t('profile.twoFactorModal.loadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (open) fetchDevices();
  }, [open, fetchDevices]);

  useEscapeToClose(open, onClose, { disabled: !!deletingId });

  if (!open) return null;

  const handleDelete = async (id) => {
    setDeletingId(id);
    setError('');
    try {
      await userService.deleteTotpDevice(id);
      setDevices((d) => d.filter((x) => x.id !== id));
      toast.success(t('profile.twoFactorModal.deviceRemoved'));
    } catch {
      setError(t('profile.twoFactorModal.deleteError'));
    } finally {
      setDeletingId(null);
    }
  };

  const handleAddDevice = () => {
    // Keycloak's CONFIGURE_TOTP required action shows a themed QR setup screen
    // (login theme covers this). After completion, user returns to /profile.
    //
    // Mevcut cihaz ID'lerini sessionStorage'a kaydet — geri dönünce ProfilePage
    // listeyi yeniden çekip karşılaştıracak. Bu, Keycloak'ın `kc_action_status`
    // parametresine bağlı kalmadan eklenen cihazı tespit etmeyi mümkün kılar.
    const beforeIds = devices.map((d) => d.id);
    try {
      sessionStorage.setItem('pending_2fa_setup', JSON.stringify(beforeIds));
    } catch {
      // sessionStorage erişilemezse bile akış devam etsin — mail kaçar, kritik değil.
    }
    redirectToKeycloakLogin({
      redirectUri: window.location.origin + '/profile',
      action: 'CONFIGURE_TOTP',
    });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget && !deletingId) onClose(); }}
    >
      <div
        className="w-full max-w-md sm:max-w-lg rounded-2xl border shadow-xl flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
      >
        {/* Header */}
        <div
          className="flex items-center gap-3 px-5 py-4 border-b flex-shrink-0"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <div
            className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg"
            style={{ backgroundColor: 'rgba(16,185,129,0.12)' }}
          >
            <ShieldCheck className="h-4 w-4" style={{ color: '#10b981' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-sm font-bold break-words" style={{ color: 'var(--text-primary)' }}>
              {t('profile.twoFactorModal.title')}
            </h2>
            <p className="text-xs mt-0.5 break-words" style={{ color: 'var(--text-secondary)' }}>
              {t('profile.twoFactorModal.subtitle')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={!!deletingId}
            className="flex h-8 w-8 items-center justify-center rounded-md disabled:opacity-50"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('common.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Body */}
        <div className="px-5 py-4 overflow-y-auto flex-1">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div
                className="h-7 w-7 rounded-full border-[3px] animate-spin"
                style={{ borderColor: 'var(--border-color)', borderTopColor: '#10b981' }}
              />
            </div>
          ) : error ? (
            <div
              className="rounded-md border px-3 py-2 text-xs font-medium"
              style={{ backgroundColor: 'rgba(239,68,68,0.08)', borderColor: 'rgba(239,68,68,0.3)', color: '#ef4444' }}
            >
              {error}
            </div>
          ) : devices.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-center gap-3">
              <div
                className="flex h-12 w-12 items-center justify-center rounded-2xl"
                style={{ backgroundColor: 'var(--bg-surface-secondary)' }}
              >
                <Smartphone className="h-6 w-6" style={{ color: 'var(--text-tertiary)' }} />
              </div>
              <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                {t('profile.twoFactorModal.noDevices')}
              </p>
              <p className="text-xs max-w-xs" style={{ color: 'var(--text-tertiary)' }}>
                {t('profile.twoFactorModal.noDevicesHint')}
              </p>
            </div>
          ) : (
            <div className="space-y-2">
              {devices.map((d) => (
                <DeviceRow
                  key={d.id}
                  device={d}
                  lang={lang}
                  onDelete={handleDelete}
                  deletingId={deletingId}
                  t={t}
                />
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 sm:gap-3 px-5 py-3 border-t flex-shrink-0"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <button
            type="button"
            onClick={onClose}
            disabled={!!deletingId}
            className="rounded-md border px-3 py-2 text-sm font-semibold transition-colors disabled:opacity-50 w-full sm:w-auto"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
          >
            {t('profile.cancel')}
          </button>
          <button
            type="button"
            onClick={handleAddDevice}
            disabled={!!deletingId}
            className="inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-white transition-colors disabled:opacity-60 w-full sm:w-auto"
            style={{ backgroundColor: '#10b981' }}
          >
            <Plus className="h-3.5 w-3.5" />
            {t('profile.twoFactorModal.addDevice')}
          </button>
        </div>
      </div>
    </div>
  );
}
