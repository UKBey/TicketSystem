/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import i18n from '../i18n';

const ToastContext = createContext(null);

const DEFAULT_TTL = 5000;
// Rate-limit uyarısı için TTL üst sınırı — retryAfter çok uzun olsa bile toast ekranı
// kilitlemesin; yine de çarpı ile erkenden kapatılabilir.
const MAX_TTL = 10000;
// Çıkış animasyonu süresi (index.css .toast-out ile eşleşir) — toast bu süre
// boyunca ekranda kalıp animasyonunu tamamladıktan sonra DOM'dan kaldırılır.
const EXIT_MS = 200;
const ICONS  = { success: CheckCircle2, error: AlertCircle, warning: AlertTriangle, info: Info };
const COLORS = {
  success: { border: 'rgba(16,185,129,0.25)', icon: '#10b981' },
  error:   { border: 'rgba(239,68,68,0.25)',  icon: '#ef4444' },
  warning: { border: 'rgba(245,158,11,0.25)', icon: '#f59e0b' },
  info:    { border: 'rgba(59,130,246,0.25)', icon: '#3b82f6' },
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  // Çıkış animasyonunu tetikle: önce toast'ı "exiting" işaretle (.toast-out devreye
  // girer), animasyon bitince DOM'dan kaldır.
  const dismiss = useCallback((id) => {
    setToasts((prev) => {
      // Zaten kapanıyorsa tekrar zamanlayıcı kurma (TTL + manuel kapatma yarışı).
      if (!prev.some((t) => t.id === id && !t.exiting)) return prev;
      return prev.map((t) => (t.id === id ? { ...t, exiting: true } : t));
    });
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, EXIT_MS);
  }, []);

  const push = useCallback((type, message, options = {}) => {
    if (!message) return null;
    const id = ++idRef.current;
    const ttl = options.ttl ?? DEFAULT_TTL;
    setToasts((prev) => [...prev, { id, type, message }]);
    if (ttl > 0) {
      setTimeout(() => dismiss(id), ttl);
    }
    return id;
  }, [dismiss]);

  const api = useMemo(() => ({
    success: (msg, opts) => push('success', msg, opts),
    error:   (msg, opts) => push('error',   msg, opts),
    warning: (msg, opts) => push('warning', msg, opts),
    info:    (msg, opts) => push('info',    msg, opts),
    dismiss,
  }), [push, dismiss]);

  // Tüm bildirimler tek noktadan aksın diye React dışı kaynaklar (örn. axios
  // interceptor'ı) de window event'i üzerinden bu sisteme toast düşürebilir.
  useEffect(() => {
    // Rate-limit (429): eski ayrı RateLimitToast bileşeninin yerini alır.
    const onRateLimit = (event) => {
      const retryAfter = event.detail?.retryAfter || 60;
      push('warning', i18n.t('rateLimit.message', { seconds: retryAfter }), {
        ttl: Math.min(retryAfter * 1000, MAX_TTL),
      });
    };
    // Genel amaçlı köprü: { type, message, ttl }
    const onAppToast = (event) => {
      const { type = 'info', message, ttl } = event.detail || {};
      push(type, message, { ttl });
    };
    window.addEventListener('rate-limit-exceeded', onRateLimit);
    window.addEventListener('app:toast', onAppToast);
    return () => {
      window.removeEventListener('rate-limit-exceeded', onRateLimit);
      window.removeEventListener('app:toast', onAppToast);
    };
  }, [push]);

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
            className={`flex items-start gap-3 rounded-lg border px-4 py-3 shadow-lg pointer-events-auto sm:min-w-[320px] ${toast.exiting ? 'toast-out' : 'toast-in'}`}
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
