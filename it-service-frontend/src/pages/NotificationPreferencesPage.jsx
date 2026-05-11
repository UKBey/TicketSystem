import { useState, useEffect } from 'react';
import {
  Bell, Mail, CheckCircle2, TicketCheck, UserCheck,
  RefreshCw, MessageSquare, AlertTriangle, ShieldAlert, Save,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getPreferences, updatePreferences } from '../services/notificationApi';

/* ── Per-event visual config ────────────────────────────────── */
const EVENT_CONFIG = [
  {
    key:        'TicketCreated',
    icon:       TicketCheck,
    iconColor:  '#3b82f6',
    iconBg:     'rgba(59,130,246,0.12)',
  },
  {
    key:        'TicketAssigned',
    icon:       UserCheck,
    iconColor:  '#8b5cf6',
    iconBg:     'rgba(139,92,246,0.12)',
  },
  {
    key:        'StatusChanged',
    icon:       RefreshCw,
    iconColor:  '#0ea5e9',
    iconBg:     'rgba(14,165,233,0.12)',
  },
  {
    key:        'CommentAdded',
    icon:       MessageSquare,
    iconColor:  '#22c55e',
    iconBg:     'rgba(34,197,94,0.12)',
  },
  {
    key:        'SlaWarning',
    icon:       AlertTriangle,
    iconColor:  '#f59e0b',
    iconBg:     'rgba(245,158,11,0.12)',
  },
  {
    key:        'SlaBreached',
    icon:       ShieldAlert,
    iconColor:  '#ef4444',
    iconBg:     'rgba(239,68,68,0.12)',
  },
  {
    key:        'TicketResolved',
    icon:       CheckCircle2,
    iconColor:  '#10b981',
    iconBg:     'rgba(16,185,129,0.12)',
  },
];

/* ── Toggle ─────────────────────────────────────────────────── */
function Toggle({ checked, onChange, label }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 focus:outline-none focus-visible:ring-2"
      style={{
        backgroundColor: checked ? '#3b82f6' : 'var(--border-color)',
        boxShadow: checked ? '0 0 0 0px rgba(59,130,246,0)' : undefined,
      }}
    >
      <span
        className="pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform duration-200"
        style={{ transform: `translateX(${checked ? '17px' : '2px'})`, marginTop: '2px' }}
      />
    </button>
  );
}

/* ── Channel pill header ─────────────────────────────────────── */
function ChannelHeader({ icon: Icon, label, color, bg }) {
  return (
    <div
      className="group relative flex h-8 w-9 items-center justify-center rounded-lg"
      style={{ backgroundColor: bg }}
      title={label}
    >
      <Icon className="h-4 w-4" style={{ color }} />
    </div>
  );
}

/* ── Event row card ──────────────────────────────────────────── */
function EventRow({ config, emailChecked, notifyChecked, onEmailChange, onNotifyChange, t }) {
  const Icon = config.icon;
  const emailLabel  = `${t(`notificationPrefs.event.${config.key}`)} — ${t('notificationPrefs.colEmail')}`;
  const notifyLabel = `${t(`notificationPrefs.event.${config.key}`)} — ${t('notificationPrefs.colInApp')}`;

  return (
    <div
      className="flex items-center gap-4 rounded-xl px-4 py-3.5 transition-colors duration-150"
      style={{ backgroundColor: 'var(--bg-surface-secondary)', border: '1px solid var(--border-color)' }}
    >
      {/* Icon */}
      <div
        className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl"
        style={{ backgroundColor: config.iconBg }}
      >
        <Icon className="h-4 w-4" style={{ color: config.iconColor }} />
      </div>

      {/* Label */}
      <span className="flex-1 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
        {t(`notificationPrefs.event.${config.key}`)}
      </span>

      {/* Toggles */}
      <div className="flex items-center gap-8">
        <Toggle checked={emailChecked}  onChange={onEmailChange}  label={emailLabel} />
        <Toggle checked={notifyChecked} onChange={onNotifyChange} label={notifyLabel} />
      </div>
    </div>
  );
}

/* ── Feedback banner ─────────────────────────────────────────── */
function FeedbackBanner({ feedback }) {
  if (!feedback) return null;
  const isSuccess = feedback.type === 'success';
  return (
    <div
      className="flex items-center gap-2.5 rounded-xl px-4 py-3 text-sm font-medium animate-fade-in"
      style={{
        backgroundColor: isSuccess ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)',
        border: `1px solid ${isSuccess ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
        color: isSuccess ? '#10b981' : '#ef4444',
      }}
    >
      {isSuccess
        ? <CheckCircle2 className="h-4 w-4 flex-shrink-0" />
        : <AlertTriangle className="h-4 w-4 flex-shrink-0" />}
      {feedback.message}
    </div>
  );
}

/* ── Main page ───────────────────────────────────────────────── */
export default function NotificationPreferencesPage() {
  const { t } = useTranslation();
  const [prefs, setPrefs]       = useState(null);
  const [loading, setLoading]   = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);

  useEffect(() => {
    getPreferences()
      .then((res) => setPrefs(res.data))
      .catch(() => setFeedback({ type: 'error', message: t('notificationPrefs.errorLoad') }))
      .finally(() => setLoading(false));
  }, [t]);

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
    <div className="animate-fade-in">
      {/* ── Page header ───────────────────────────────────── */}
      <div className="mb-7">
        <div className="flex items-center gap-3 mb-1">
          <div
            className="flex h-9 w-9 items-center justify-center rounded-xl"
            style={{ background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)' }}
          >
            <Bell className="h-4 w-4 text-white" />
          </div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('notificationPrefs.title')}
          </h1>
        </div>
        <p className="text-sm ml-12" style={{ color: 'var(--text-secondary)' }}>
          {t('notificationPrefs.subtitle')}
        </p>
      </div>

      <div className="max-w-2xl space-y-4">
        {/* ── Main card ─────────────────────────────────── */}
        <div
          className="rounded-2xl border overflow-hidden"
          style={{
            backgroundColor: 'var(--bg-surface)',
            borderColor: 'var(--border-color)',
            boxShadow: 'var(--shadow-md)',
          }}
        >
          {/* Card header */}
          <div
            className="px-6 py-4 border-b"
            style={{ borderColor: 'var(--border-color)' }}
          >
            {/* Channel column headers */}
            <div className="flex items-center">
              <span
                className="flex-1 text-xs font-semibold uppercase tracking-wide"
                style={{ color: 'var(--text-tertiary)' }}
              >
                {t('notificationPrefs.colEvent')}
              </span>
              <div className="flex items-center gap-8 pr-1">
                <ChannelHeader
                  icon={Mail}
                  label={t('notificationPrefs.colEmail')}
                  color="#3b82f6"
                  bg="rgba(59,130,246,0.1)"
                />
                <ChannelHeader
                  icon={Bell}
                  label={t('notificationPrefs.colInApp')}
                  color="#8b5cf6"
                  bg="rgba(139,92,246,0.1)"
                />
              </div>
            </div>
          </div>

          {/* Rows */}
          <div className="p-4 space-y-2.5">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <div
                  className="h-7 w-7 rounded-full border-[3px] animate-spin"
                  style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
                />
              </div>
            ) : (
              EVENT_CONFIG.map((config) => {
                const emailKey  = `emailOn${config.key}`;
                const notifyKey = `notifyOn${config.key}`;
                return (
                  <EventRow
                    key={config.key}
                    config={config}
                    emailChecked={prefs?.[emailKey]  ?? true}
                    notifyChecked={prefs?.[notifyKey] ?? true}
                    onEmailChange={(val)  => handleToggle(emailKey,  val)}
                    onNotifyChange={(val) => handleToggle(notifyKey, val)}
                    t={t}
                  />
                );
              })
            )}
          </div>

          {/* Footer */}
          {!loading && (
            <div
              className="px-4 pb-4 pt-1 flex items-center justify-between gap-4"
            >
              <FeedbackBanner feedback={feedback} />
              {!feedback && <span />}
              <button
                onClick={handleSave}
                disabled={isSaving}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-60 cursor-pointer flex-shrink-0"
                style={{
                  background: isSaving
                    ? '#6b7280'
                    : 'linear-gradient(135deg, #3b82f6 0%, #6366f1 100%)',
                  boxShadow: isSaving ? 'none' : '0 2px 12px rgba(59,130,246,0.35)',
                }}
              >
                <Save className="h-4 w-4" />
                {isSaving ? t('notificationPrefs.saving') : t('notificationPrefs.save')}
              </button>
            </div>
          )}
        </div>

        {/* ── Info note ─────────────────────────────────── */}
        {!loading && (
          <p className="text-xs px-1 animate-fade-in" style={{ color: 'var(--text-tertiary)' }}>
            {t('notificationPrefs.retentionNote')}
          </p>
        )}
      </div>
    </div>
  );
}
