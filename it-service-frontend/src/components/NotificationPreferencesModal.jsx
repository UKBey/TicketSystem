import { useState, useEffect } from 'react';
import {
  Bell, Mail, X, CheckCircle2, TicketCheck, UserCheck,
  RefreshCw, MessageSquare, AlertTriangle, ShieldAlert, Save,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getPreferences, updatePreferences } from '../services/notificationApi';
import { useAuth } from '../context/AuthContext';
import { useEscapeToClose } from '../hooks/useEscapeToClose';

/* ── Per-event visual config ────────────────────────────────── */
// `roles`: hangi rol(ler) bu bildirimi GERÇEKTEN alır → yalnızca o rollerdeki
// kullanıcıya tercih satırı gösterilir. 'AGENT' lead_agent'ı da kapsar.
const EVENT_CONFIG = [
  { key: 'TicketCreated',  roles: ['CUSTOMER'],          icon: TicketCheck,   iconColor: '#3b82f6', iconBg: 'rgba(59,130,246,0.12)' },
  { key: 'TicketAssigned', roles: ['AGENT'],             icon: UserCheck,     iconColor: '#8b5cf6', iconBg: 'rgba(139,92,246,0.12)' },
  { key: 'StatusChanged',  roles: ['CUSTOMER'],          icon: RefreshCw,     iconColor: '#0ea5e9', iconBg: 'rgba(14,165,233,0.12)' },
  { key: 'CommentAdded',   roles: ['CUSTOMER', 'AGENT'], icon: MessageSquare, iconColor: '#22c55e', iconBg: 'rgba(34,197,94,0.12)' },
  { key: 'SlaWarning',     roles: ['AGENT', 'MANAGER'],  icon: AlertTriangle, iconColor: '#f59e0b', iconBg: 'rgba(245,158,11,0.12)' },
  { key: 'SlaBreached',    roles: ['AGENT', 'MANAGER'],  icon: ShieldAlert,   iconColor: '#ef4444', iconBg: 'rgba(239,68,68,0.12)' },
  { key: 'TicketResolved', roles: ['CUSTOMER'],          icon: CheckCircle2,  iconColor: '#10b981', iconBg: 'rgba(16,185,129,0.12)' },
];

function Toggle({ checked, onChange, label }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 focus:outline-none focus-visible:ring-2"
      style={{ backgroundColor: checked ? '#3b82f6' : 'var(--border-color)' }}
    >
      <span
        className="pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform duration-200"
        style={{ transform: `translateX(${checked ? '17px' : '2px'})`, marginTop: '2px' }}
      />
    </button>
  );
}

function ChannelHeader({ icon: Icon, label, color, bg }) {
  return (
    <div className="group relative flex h-8 w-9 items-center justify-center rounded-lg" style={{ backgroundColor: bg }} title={label}>
      <Icon className="h-4 w-4" style={{ color }} />
    </div>
  );
}

function EventRow({ config, emailChecked, notifyChecked, onEmailChange, onNotifyChange, t }) {
  const Icon = config.icon;
  const emailLabel  = `${t(`notificationPrefs.event.${config.key}`)} — ${t('notificationPrefs.colEmail')}`;
  const notifyLabel = `${t(`notificationPrefs.event.${config.key}`)} — ${t('notificationPrefs.colInApp')}`;

  return (
    <div
      className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 rounded-xl px-4 py-3.5 transition-colors duration-150"
      style={{ backgroundColor: 'var(--bg-surface-secondary)', border: '1px solid var(--border-color)' }}
    >
      <div className="flex items-center gap-3 sm:gap-4 flex-1 min-w-0">
        <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl" style={{ backgroundColor: config.iconBg }}>
          <Icon className="h-4 w-4" style={{ color: config.iconColor }} />
        </div>
        <span className="flex-1 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
          {t(`notificationPrefs.event.${config.key}`)}
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-4 sm:gap-8 sm:pl-0 pl-12">
        <div className="flex items-center gap-2">
          <span className="sm:hidden text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('notificationPrefs.colEmail')}</span>
          <Toggle checked={emailChecked} onChange={onEmailChange} label={emailLabel} />
        </div>
        <div className="flex items-center gap-2">
          <span className="sm:hidden text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('notificationPrefs.colInApp')}</span>
          <Toggle checked={notifyChecked} onChange={onNotifyChange} label={notifyLabel} />
        </div>
      </div>
    </div>
  );
}

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
      {isSuccess ? <CheckCircle2 className="h-4 w-4 flex-shrink-0" /> : <AlertTriangle className="h-4 w-4 flex-shrink-0" />}
      {feedback.message}
    </div>
  );
}

/**
 * Kullanıcının olay-bazlı e-posta / uygulama-içi bildirim tercihlerini yönettiği modal.
 */
export default function NotificationPreferencesModal({ open, onClose }) {
  const { t } = useTranslation();
  const { isCustomer, isAgent, isManager } = useAuth();
  const [prefs, setPrefs]       = useState(null);
  const [loading, setLoading]   = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);

  const roleActive = { CUSTOMER: isCustomer, AGENT: isAgent, MANAGER: isManager };
  const visibleEvents = EVENT_CONFIG.filter((e) => e.roles.some((r) => roleActive[r]));

  useEscapeToClose(open, onClose, { disabled: isSaving });

  // Modal her açıldığında tercihleri yeniden yükle.
  useEffect(() => {
    if (!open) return;
    setLoading(true);
    setFeedback(null);
    getPreferences()
      .then((res) => setPrefs(res.data))
      .catch(() => setFeedback({ type: 'error', message: t('notificationPrefs.errorLoad') }))
      .finally(() => setLoading(false));
  }, [open, t]);

  if (!open) return null;

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
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget && !isSaving) onClose(); }}
    >
      <div
        className="w-full max-w-lg max-h-[90vh] flex flex-col rounded-2xl border shadow-xl animate-fade-in"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'rgba(139,92,246,0.12)' }}>
            <Bell className="h-4 w-4" style={{ color: '#8b5cf6' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-base font-bold leading-tight" style={{ color: 'var(--text-primary)' }}>
              {t('notificationPrefs.title')}
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              {t('notificationPrefs.subtitle')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
            style={{ color: 'var(--text-tertiary)' }}
            aria-label={t('common.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Channel column headers (sm+) */}
        {!loading && visibleEvents.length > 0 && (
          <div className="hidden sm:flex items-center px-5 py-2.5 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <span className="flex-1 text-[11px] font-semibold uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
              {t('notificationPrefs.colEvent')}
            </span>
            <div className="flex items-center gap-8 pr-1">
              <ChannelHeader icon={Mail} label={t('notificationPrefs.colEmail')} color="#3b82f6" bg="rgba(59,130,246,0.1)" />
              <ChannelHeader icon={Bell} label={t('notificationPrefs.colInApp')} color="#8b5cf6" bg="rgba(139,92,246,0.1)" />
            </div>
          </div>
        )}

        {/* Rows (scrollable) */}
        <div className="p-4 space-y-2.5 overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="h-7 w-7 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
            </div>
          ) : visibleEvents.length === 0 ? (
            <div className="py-10 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
              {t('notificationPrefs.noneForRole')}
            </div>
          ) : (
            visibleEvents.map((config) => {
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
        {!loading && visibleEvents.length > 0 && (
          <div className="px-4 py-3 border-t flex-shrink-0 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3" style={{ borderColor: 'var(--border-color)' }}>
            <FeedbackBanner feedback={feedback} />
            {!feedback && <span className="hidden sm:block text-[11px]" style={{ color: 'var(--text-tertiary)' }}>{t('notificationPrefs.retentionNote')}</span>}
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold text-white transition-all duration-200 disabled:opacity-60 cursor-pointer flex-shrink-0 w-full sm:w-auto"
              style={{
                background: isSaving ? '#6b7280' : 'linear-gradient(135deg, #3b82f6 0%, #6366f1 100%)',
                boxShadow: isSaving ? 'none' : '0 2px 12px rgba(59,130,246,0.35)',
              }}
            >
              <Save className="h-4 w-4" />
              {isSaving ? t('notificationPrefs.saving') : t('notificationPrefs.save')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
