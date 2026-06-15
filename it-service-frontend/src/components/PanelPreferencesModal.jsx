import {
  LayoutPanelLeft, X, Briefcase, Inbox, History, Users, Layers,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useEscapeToClose } from '../hooks/useEscapeToClose';
import { usePanelPrefs, PANEL_KEYS } from '../context/PanelPrefsContext';

/* ── Per-panel visual config — sidebar ikon/etiketleriyle hizalı ──── */
const PANEL_CONFIG = {
  workspace:  { icon: Briefcase, iconColor: '#8b5cf6', iconBg: 'rgba(139,92,246,0.12)', labelKey: 'sidebar.workspace'   },
  pool:       { icon: Inbox,     iconColor: '#3b82f6', iconBg: 'rgba(59,130,246,0.12)', labelKey: 'sidebar.pool'        },
  history:    { icon: History,   iconColor: '#0ea5e9', iconBg: 'rgba(14,165,233,0.12)', labelKey: 'sidebar.history'     },
  team:       { icon: Users,     iconColor: '#22c55e', iconBg: 'rgba(34,197,94,0.12)',  labelKey: 'sidebar.teamTickets' },
  allTickets: { icon: Layers,    iconColor: '#f59e0b', iconBg: 'rgba(245,158,11,0.12)', labelKey: 'sidebar.allTickets'  },
};

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

/**
 * Agent/lead kullanıcıların sol menüdeki ticket panellerini açıp kapattığı modal.
 * Seçim anında kaydedilir (PanelPrefsContext → localStorage + sunucu).
 */
export default function PanelPreferencesModal({ open, onClose }) {
  const { t } = useTranslation();
  const { isPanelVisible, setPanelVisible } = usePanelPrefs();

  useEscapeToClose(open, onClose);
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        className="w-full max-w-md max-h-[90vh] flex flex-col rounded-2xl border shadow-xl animate-fade-in"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg" style={{ backgroundColor: 'rgba(99,102,241,0.12)' }}>
            <LayoutPanelLeft className="h-4 w-4" style={{ color: '#6366f1' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="text-base font-bold leading-tight" style={{ color: 'var(--text-primary)' }}>
              {t('panelPrefs.title')}
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              {t('panelPrefs.subtitle')}
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

        {/* Rows */}
        <div className="p-4 space-y-2.5 overflow-y-auto">
          {PANEL_KEYS.map((key) => {
            const config = PANEL_CONFIG[key];
            const Icon = config.icon;
            const checked = isPanelVisible(key);
            const label = t(config.labelKey);
            return (
              <div
                key={key}
                className="flex items-center gap-3 sm:gap-4 rounded-xl px-4 py-3.5 transition-colors duration-150"
                style={{ backgroundColor: 'var(--bg-surface-secondary)', border: '1px solid var(--border-color)' }}
              >
                <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl" style={{ backgroundColor: config.iconBg }}>
                  <Icon className="h-4 w-4" style={{ color: config.iconColor }} />
                </div>
                <span className="flex-1 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                  {label}
                </span>
                <Toggle checked={checked} onChange={(val) => setPanelVisible(key, val)} label={label} />
              </div>
            );
          })}
        </div>

        {/* Footer note */}
        <div className="px-5 py-3 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <p className="text-[11px]" style={{ color: 'var(--text-tertiary)' }}>
            {t('panelPrefs.note')}
          </p>
        </div>
      </div>
    </div>
  );
}
