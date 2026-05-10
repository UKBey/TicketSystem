import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { getSlaPolicies, updateSlaPolicy } from '../services/api';
import { Save, Clock } from 'lucide-react';

const PRIORITY_COLOR = {
  CRITICAL: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300',
  HIGH:     'bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300',
  MEDIUM:   'bg-yellow-100 text-yellow-700 dark:bg-yellow-500/20 dark:text-yellow-300',
  LOW:      'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300',
};

export default function SlaPolicyConfigPanel() {
  const { t } = useTranslation();
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [toastMessage, setToastMessage] = useState('');

  const fetchPolicies = useCallback(async () => {
    try {
      setLoading(true);
      const res = await getSlaPolicies();
      setPolicies(res.data);
    } catch (err) {
      console.error('Could not load SLA policies:', err);
      setError(t('admin.sla.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchPolicies();
  }, [fetchPolicies]);

  const handleChange = (id, field, value) => {
    setPolicies(policies.map(p =>
      p.id === id ? { ...p, [field]: value } : p
    ));
  };

  const handleSaveAll = async () => {
    // Validate all rows before saving
    for (const policy of policies) {
      const targetHours = parseInt(policy.targetResolutionHours, 10);
      const warningHours = parseInt(policy.warningThresholdHours, 10);

      if (isNaN(targetHours) || targetHours < 1) {
        alert(t('admin.sla.validationTarget', { priority: policy.priority }));
        return;
      }
      if (isNaN(warningHours) || warningHours < 0) {
        alert(t('admin.sla.validationWarningMin', { priority: policy.priority }));
        return;
      }
      if (warningHours > 0 && warningHours >= targetHours) {
        alert(t('admin.sla.validationWarningMax', { priority: policy.priority }));
        return;
      }
    }

    try {
      setSaving(true);
      const updated = await Promise.all(
        policies.map(policy =>
          updateSlaPolicy(policy.id, {
            targetResolutionHours: parseInt(policy.targetResolutionHours, 10),
            warningThresholdHours: parseInt(policy.warningThresholdHours, 10),
          }).then(res => res.data)
        )
      );
      setPolicies(updated);
      showToast(t('admin.sla.toastSaved'));
    } catch (err) {
      console.error('Save failed:', err);
      alert(err.response?.data?.message || t('admin.sla.errorSave'));
    } finally {
      setSaving(false);
    }
  };

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(''), 3000);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="h-6 w-6 rounded-full border-[3px] animate-spin"
          style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
      </div>
    );
  }

  return (
    <div className="rounded-xl border overflow-hidden mt-8"
      style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>

      {/* Header */}
      <div className="px-6 py-4 border-b flex items-center justify-between"
        style={{ borderColor: 'var(--border-color)' }}>
        <div className="flex items-center gap-2">
          <Clock className="h-4 w-4" style={{ color: 'var(--text-secondary)' }} />
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('admin.sla.title')}
          </span>
        </div>
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
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                {[
                  t('admin.sla.colPriority'),
                  t('admin.sla.colTarget'),
                  t('admin.sla.colWarning'),
                  t('admin.sla.colSummary'),
                ].map(h => (
                  <th key={h}
                    className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                    style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {policies.map(policy => {
                const colorClass = PRIORITY_COLOR[policy.priority] ?? '';
                // Use translated priority label from ticket.priority namespace
                const priorityLabel = t(`ticket.priority.${policy.priority.toLowerCase()}`, { defaultValue: policy.priority });
                const warningHours = parseInt(policy.warningThresholdHours, 10);
                const targetHours = parseInt(policy.targetResolutionHours, 10);

                return (
                  <tr key={policy.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                    {/* Priority badge */}
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold ${colorClass}`}>
                        {priorityLabel}
                      </span>
                    </td>

                    {/* Target resolution hours */}
                    <td className="px-4 py-3">
                      <input
                        type="number"
                        min="1"
                        value={policy.targetResolutionHours}
                        onChange={e => handleChange(policy.id, 'targetResolutionHours', e.target.value)}
                        className="w-24 rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                        style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                      />
                    </td>

                    {/* Warning threshold hours */}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min="0"
                          value={policy.warningThresholdHours}
                          onChange={e => handleChange(policy.id, 'warningThresholdHours', e.target.value)}
                          className="w-24 rounded-lg border px-2 py-1.5 text-sm outline-none transition-all focus:ring-2"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                        />
                        {warningHours === 0 && (
                          <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                            {t('admin.sla.disabled')}
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Summary */}
                    <td className="px-4 py-3">
                      <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                        {warningHours > 0
                          ? t('admin.sla.summaryWithWarning', { targetHours, warningHours })
                          : t('admin.sla.summaryNoWarning', { targetHours })}
                      </span>
                    </td>
                  </tr>
                );
              })}

              {policies.length === 0 && (
                <tr>
                  <td colSpan="4" className="text-center py-8 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    {t('admin.sla.noPolicies')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Footer with single Save button */}
      {!error && policies.length > 0 && (
        <div className="px-6 py-4 border-t flex items-center justify-between"
          style={{ borderColor: 'var(--border-color)' }}>
          <p className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
            {t('admin.sla.footerHint')}
          </p>
          <button
            onClick={handleSaveAll}
            disabled={saving}
            className="inline-flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
          >
            {saving ? (
              <div className="h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
            ) : (
              <Save className="h-3.5 w-3.5" />
            )}
            {t('admin.sla.saveAll')}
          </button>
        </div>
      )}
    </div>
  );
}
