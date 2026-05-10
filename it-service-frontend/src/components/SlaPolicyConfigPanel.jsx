import { useState, useEffect } from 'react';
import { getSlaPolicies, updateSlaPolicy } from '../services/api';
import { Save, Clock } from 'lucide-react';

const PRIORITY_LABELS = {
  CRITICAL: { label: 'Critical', color: 'bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300' },
  HIGH:     { label: 'High',     color: 'bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300' },
  MEDIUM:   { label: 'Medium',   color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-500/20 dark:text-yellow-300' },
  LOW:      { label: 'Low',      color: 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300' },
};

export default function SlaPolicyConfigPanel() {
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [toastMessage, setToastMessage] = useState('');

  const fetchPolicies = async () => {
    try {
      setLoading(true);
      const res = await getSlaPolicies();
      setPolicies(res.data);
    } catch (err) {
      console.error('Could not load SLA policies:', err);
      setError('An error occurred while loading SLA policies.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPolicies();
  }, []);

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
        alert(`[${policy.priority}] Target resolution time must be at least 1 hour.`);
        return;
      }
      if (isNaN(warningHours) || warningHours < 0) {
        alert(`[${policy.priority}] Warning threshold must be 0 or greater.`);
        return;
      }
      if (warningHours > 0 && warningHours >= targetHours) {
        alert(`[${policy.priority}] Warning threshold must be less than the target resolution time.`);
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
      showToast('SLA policies saved successfully!');
    } catch (err) {
      console.error('Save failed:', err);
      alert(err.response?.data?.message || 'Could not update configurations.');
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
            SLA Policy Configuration
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
                {['Priority', 'Target Resolution (hours)', 'Warning Threshold (hours)', 'Summary'].map(h => (
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
                const meta = PRIORITY_LABELS[policy.priority] ?? { label: policy.priority, color: '' };
                const warningHours = parseInt(policy.warningThresholdHours, 10);
                const targetHours = parseInt(policy.targetResolutionHours, 10);

                return (
                  <tr key={policy.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
                    {/* Priority badge */}
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold ${meta.color}`}>
                        {meta.label}
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
                          <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>disabled</span>
                        )}
                      </div>
                    </td>

                    {/* Summary */}
                    <td className="px-4 py-3">
                      <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                        {warningHours > 0
                          ? `Resolve within ${targetHours}h, warn at ${warningHours}h remaining`
                          : `Resolve within ${targetHours}h, no warning`}
                      </span>
                    </td>
                  </tr>
                );
              })}

              {policies.length === 0 && (
                <tr>
                  <td colSpan="4" className="text-center py-8 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    No SLA policies found.
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
            A warning threshold of 0 disables notifications for that priority. Changes take effect immediately.
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
            Save All
          </button>
        </div>
      )}
    </div>
  );
}
