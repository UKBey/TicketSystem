/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

const ToastContext = createContext(null);

const DEFAULT_TTL = 5000;
const ICONS  = { success: CheckCircle2, error: AlertCircle, info: Info };
const COLORS = {
  success: { border: 'rgba(16,185,129,0.25)', icon: '#10b981' },
  error:   { border: 'rgba(239,68,68,0.25)',  icon: '#ef4444' },
  info:    { border: 'rgba(59,130,246,0.25)', icon: '#3b82f6' },
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((type, message, options = {}) => {
    if (!message) return null;
    const id = ++idRef.current;
    const ttl = options.ttl ?? DEFAULT_TTL;
    setToasts((prev) => [...prev, { id, type, message }]);
    if (ttl > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, ttl);
    }
    return id;
  }, []);

  const api = useMemo(() => ({
    success: (msg, opts) => push('success', msg, opts),
    error:   (msg, opts) => push('error',   msg, opts),
    info:    (msg, opts) => push('info',    msg, opts),
    dismiss,
  }), [push, dismiss]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <ToastViewport toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  );
}

function ToastViewport({ toasts, onDismiss }) {
  const { t } = useTranslation();
  if (toasts.length === 0) return null;
  return (
    <div className="fixed top-4 inset-x-4 sm:inset-x-auto sm:right-4 sm:left-auto z-[60] flex flex-col gap-2 sm:w-auto sm:max-w-md pointer-events-none">
      {toasts.map((toast) => {
        const Icon  = ICONS[toast.type] ?? Info;
        const color = COLORS[toast.type] ?? COLORS.info;
        return (
          <div
            key={toast.id}
            role={toast.type === 'error' ? 'alert' : 'status'}
            className="flex items-start gap-3 rounded-lg border px-4 py-3 shadow-lg pointer-events-auto sm:min-w-[320px]"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: color.border }}
          >
            <Icon className="h-5 w-5 shrink-0 mt-0.5" style={{ color: color.icon }} />
            <div className="flex-1 text-sm font-medium break-words" style={{ color: 'var(--text-primary)' }}>
              {toast.message}
            </div>
            <button
              type="button"
              onClick={() => onDismiss(toast.id)}
              aria-label={t('toast.close')}
              className="rounded-md p-1 transition-colors hover:bg-[var(--bg-surface-hover)]"
              style={{ color: 'var(--text-tertiary)' }}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return ctx;
}
