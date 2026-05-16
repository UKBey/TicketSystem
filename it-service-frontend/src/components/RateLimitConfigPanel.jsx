import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { getRateLimitConfigs, updateRateLimitConfig } from '../services/api';
import { Save } from 'lucide-react';

export default function RateLimitConfigPanel() {
  const { t } = useTranslation();
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingId, setSavingId] = useState(null);
  const [toastMessage, setToastMessage] = useState('');

  const fetchConfigs = useCallback(async () => {
    try {
      setLoading(true);
      const res = await getRateLimitConfigs();
      setConfigs(res.data);
    } catch (err) {
      console.error('Could not load rate limits:', err);
      setError(t('admin.rateLimits.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchConfigs();
  }, [fetchConfigs]);

  const handleChange = (id, field, value) => {
    setConfigs(configs.map(config => 
      config.id === id ? { ...config, [field]: value } : config
    ));
  };

  const handleSave = async (id) => {
    const configToUpdate = configs.find(c => c.id === id);
    if (!configToUpdate) return;

    try {
      setSavingId(id);
      const { maxRequests, durationSeconds, enabled } = configToUpdate;
      const res = await updateRateLimitConfig(id, {
        maxRequests: parseInt(maxRequests, 10),
        durationSeconds: parseInt(durationSeconds, 10),
        enabled
      });
      
      setConfigs(configs.map(config => 
        config.id === id ? res.data : config
      ));
      
      showToast(t('admin.rateLimits.toastSaved'));
    } catch (err) {
      console.error('Save failed:', err);
      alert(err.response?.data?.message || t('admin.rateLimits.errorSave'));
    } finally {
      setSavingId(null);
    }
  };

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(''), 3000);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="h-6 w-6 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  return (
    <div className="rounded-xl border overflow-hidden mt-8" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
      <div className="px-4 py-4 border-b font-semibold text-sm flex flex-col sm:flex-row sm:justify-between sm:items-center gap-2 sm:px-6" style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}>
        <span>{t('admin.rateLimits.title')}</span>
        {toastMessage && (
          <span className="text-xs font-medium text-success-600 dark:text-success-400 animate-pulse">
            {toastMessage}
          </span>
        )}
      </div>
      
      {error ? (
        <div className="p-4">
          <div className="rounded-lg px-4 py-3 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
            {error}
          </div>
        </div>
      ) : (
        <>
          <div className="space-y-3 p-4 sm:hidden">
            {configs.map(config => {
              const reqPerMin = Math.round((config.maxRequests / config.durationSeconds) * 60);

              return (
                <div key={config.id} className="rounded-xl border p-4" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                  <div>
                    <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{config.description}</div>
                    <div className="text-xs mt-0.5 break-all" style={{ color: 'var(--text-tertiary)' }}>{config.endpointKey}</div>
                  </div>
                  <dl className="mt-4 space-y-3">
                    <div>
                      <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('admin.rateLimits.colMaxRequests')}</dt>
                      <dd className="mt-1">
                        <input
                          type="number"
                          min="1"
                          value={config.maxRequests}
                          onChange={(e) => handleChange(config.id, 'maxRequests', e.target.value)}
                          className="w-full rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </dd>
                    </div>
                    <div>
                      <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('admin.rateLimits.colDuration')}</dt>
                      <dd className="mt-1">
                        <input
                          type="number"
                          min="1"
                          value={config.durationSeconds}
                          onChange={(e) => handleChange(config.id, 'durationSeconds', e.target.value)}
                          className="w-full rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </dd>
                    </div>
                    <div>
                      <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('admin.rateLimits.colSummary')}</dt>
                      <dd className="mt-1 text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                        {t('admin.rateLimits.reqPerMin', { count: reqPerMin })}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>{t('admin.rateLimits.colActive')}</dt>
                      <dd className="mt-1">
                        <label className="inline-flex items-center gap-2 text-sm" style={{ color: 'var(--text-primary)' }}>
                          <input
                            type="checkbox"
                            checked={config.enabled}
                            onChange={(e) => handleChange(config.id, 'enabled', e.target.checked)}
                            className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-600 cursor-pointer"
                          />
                          <span>{config.enabled ? t('admin.rateLimits.colActive') : '—'}</span>
                        </label>
                      </dd>
                    </div>
                  </dl>
                  <div className="mt-4">
                    <button
                      onClick={() => handleSave(config.id)}
                      disabled={savingId === config.id}
                      className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                    >
                      {savingId === config.id ? (
                        <div className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                      ) : (
                        <Save className="h-3.5 w-3.5" />
                      )}
                      {t('admin.rateLimits.save')}
                    </button>
                  </div>
                </div>
              );
            })}

            {configs.length === 0 && (
              <div className="text-center py-8 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                {t('admin.rateLimits.noConfigs')}
              </div>
            )}
          </div>

          <div className="hidden sm:block">
            <table className="w-full">
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colEndpoint')}</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colMaxRequests')}</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colDuration')}</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colSummary')}</th>
                  <th className="text-center px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colActive')}</th>
                  <th className="text-right px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('admin.rateLimits.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {configs.map(config => {
                  const reqPerMin = Math.round((config.maxRequests / config.durationSeconds) * 60);

                  return (
                    <tr key={config.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{config.description}</div>
                        <div className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>{config.endpointKey}</div>
                      </td>
                      <td className="px-4 py-3">
                        <input
                          type="number"
                          min="1"
                          value={config.maxRequests}
                          onChange={(e) => handleChange(config.id, 'maxRequests', e.target.value)}
                          className="w-20 rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </td>
                      <td className="px-4 py-3">
                        <input
                          type="number"
                          min="1"
                          value={config.durationSeconds}
                          onChange={(e) => handleChange(config.id, 'durationSeconds', e.target.value)}
                          className="w-20 rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs font-medium" style={{ color: 'var(--text-secondary)' }}>
                          {t('admin.rateLimits.reqPerMin', { count: reqPerMin })}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <input
                          type="checkbox"
                          checked={config.enabled}
                          onChange={(e) => handleChange(config.id, 'enabled', e.target.checked)}
                          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-600 cursor-pointer"
                        />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleSave(config.id)}
                          disabled={savingId === config.id}
                          className="inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                        >
                          {savingId === config.id ? (
                            <div className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
                          ) : (
                            <Save className="h-3.5 w-3.5" />
                          )}
                          {t('admin.rateLimits.save')}
                        </button>
                      </td>
                    </tr>
                  );
                })}

                {configs.length === 0 && (
                  <tr>
                    <td colSpan="6" className="text-center py-8 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                      {t('admin.rateLimits.noConfigs')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
