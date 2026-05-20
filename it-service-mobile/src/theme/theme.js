/**
 * Renk paletleri — web'deki CSS değişkenlerinin (index.css) mobil karşılığı.
 * Görsel birebir aynılık hedeflenmiyor; tutarlı bir açık/koyu tema yeterli.
 */
export const lightTheme = {
  dark: false,
  bgBody: '#f1f5f9',
  bgSurface: '#ffffff',
  bgSurfaceSecondary: '#f8fafc',
  bgInput: '#ffffff',
  textPrimary: '#0f172a',
  textSecondary: '#475569',
  textTertiary: '#94a3b8',
  border: '#e2e8f0',
  primary: '#3b82f6',
  primaryDark: '#2563eb',
  onPrimary: '#ffffff',
  danger: '#ef4444',
  warning: '#f59e0b',
  success: '#10b981',
  overlay: 'rgba(0,0,0,0.5)',
};

export const darkTheme = {
  dark: true,
  bgBody: '#0f172a',
  bgSurface: '#1e293b',
  bgSurfaceSecondary: '#334155',
  bgInput: '#1e293b',
  textPrimary: '#f1f5f9',
  textSecondary: '#94a3b8',
  textTertiary: '#64748b',
  border: '#334155',
  primary: '#3b82f6',
  primaryDark: '#2563eb',
  onPrimary: '#ffffff',
  danger: '#f87171',
  warning: '#fbbf24',
  success: '#34d399',
  overlay: 'rgba(0,0,0,0.6)',
};

/** Bilet durumu / öncelik renkleri — listelerde ve rozetlerde kullanılır. */
export const STATUS_COLORS = {
  NEW: '#3b82f6',
  IN_PROGRESS: '#f59e0b',
  WAITING_FOR_CUSTOMER: '#a855f7',
  RESOLVED: '#10b981',
  CLOSED: '#64748b',
};

export const PRIORITY_COLORS = {
  LOW: '#10b981',
  MEDIUM: '#f59e0b',
  HIGH: '#f97316',
  CRITICAL: '#ef4444',
};
