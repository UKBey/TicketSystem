import { useState, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getPreferences, updatePreferences } from '../services/notificationApi';

const EVENT_KEYS = [
  'TicketCreated',
  'TicketAssigned',
  'StatusChanged',
  'CommentAdded',
  'SlaWarning',
  'SlaBreached',
  'TicketResolved',
];

function Toggle({ checked, onChange }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 focus:outline-none"
      style={{ backgroundColor: checked ? '#3b82f6' : 'var(--border-color)' }}
    >
      <span
        className="pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow transition-transform duration-200"
        style={{ transform: `translateX(${checked ? '17px' : '2px'})`, marginTop: '2px' }}
      />
    </button>
  );
}

export default function NotificationPreferencesPage() {
  const { t } = useTranslation();
  const [prefs, setPrefs] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [feedback, setFeedback] = useState(null); // { type: 'success'|'error', message }

  useEffect(() => {
    getPreferences()
      .then((res) => setPrefs(res.data))
      .catch(() => setFeedback({ type: 'error', message: t('notificationPrefs.errorLoad') }))
      .finally(() => setLoading(false));
  }, []);

  const handleToggle = (key, value) => {
    setPrefs((prev) => ({ ...prev, [key]: value }));
    setFeedback(null);
  };

  const handleSave = async () => {
    setIsSaving(true);
    setFeedback(null);
    try {
      const res = await updatePreferences(prefs);
      setPrefs(res.data);
      setFeedback({ type: 'success', message: t('notificationPrefs.saved') });
    } catch {
      setFeedback({ type: 'error', message: t('notificationPrefs.errorSave') });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
          {t('notificationPrefs.title')}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          {t('notificationPrefs.subtitle')}
        </p>
      </div>

      <div className="max-w-2xl">
        <div
          className="rounded-xl border"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-sm)',
          }}
        >
          <div
            className="px-6 py-4 border-b font-semibold text-sm flex items-center gap-2"
            style={{ borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
          >
            <Bell className="h-4 w-4" style={{ color: 'var(--text-tertiary)' }} />
            {t('notificationPrefs.channelsTitle')}
          </div>

          <div className="p-6">
            {loading ? (
              <div className="flex items-center justify-center py-10">
                <div
                  className="h-6 w-6 rounded-full border-[3px] animate-spin"
                  style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
                />
              </div>
            ) : (
              <div>
                {/* Column headers */}
                <div className="grid items-center mb-3" style={{ gridTemplateColumns: '1fr 56px 56px' }}>
                  <span className="text-xs font-medium uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                    {t('notificationPrefs.colEvent')}
                  </span>
                  <span className="text-xs font-medium uppercase tracking-wide text-center" style={{ color: 'var(--text-tertiary)' }}>
                    {t('notificationPrefs.colEmail')}
                  </span>
                  <span className="text-xs font-medium uppercase tracking-wide text-center" style={{ color: 'var(--text-tertiary)' }}>
                    {t('notificationPrefs.colInApp')}
                  </span>
                </div>

                <div className="space-y-4">
                  {EVENT_KEYS.map((key) => {
                    const emailKey = `emailOn${key}`;
                    const notifyKey = `notifyOn${key}`;
                    return (
                      <div key={key} className="grid items-center" style={{ gridTemplateColumns: '1fr 56px 56px' }}>
                        <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                          {t(`notificationPrefs.event.${key}`)}
                        </span>
                        <div className="flex justify-center">
                          <Toggle
                            checked={prefs?.[emailKey] ?? true}
                            onChange={(val) => handleToggle(emailKey, val)}
                          />
                        </div>
                        <div className="flex justify-center">
                          <Toggle
                            checked={prefs?.[notifyKey] ?? true}
                            onChange={(val) => handleToggle(notifyKey, val)}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          {!loading && (
            <div
              className="px-6 py-4 border-t flex items-center justify-between gap-4"
              style={{ borderColor: 'var(--border-color)' }}
            >
              {feedback ? (
                <span
                  className="text-sm"
                  style={{ color: feedback.type === 'success' ? '#16a34a' : '#dc2626' }}
                >
                  {feedback.message}
                </span>
              ) : (
                <span />
              )}
              <button
                onClick={handleSave}
                disabled={isSaving}
                className="px-4 py-2 rounded-lg text-sm font-medium text-white transition-opacity disabled:opacity-60 cursor-pointer"
                style={{ backgroundColor: '#3b82f6' }}
              >
                {isSaving ? t('notificationPrefs.saving') : t('notificationPrefs.save')}
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
