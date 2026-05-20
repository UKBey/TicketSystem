import api from './client';

/** Gösterge paneli özet metrikleri (KPI kartları). */
export const getDashboardSummary = () => api.get('/metrics/dashboard-summary');

/** Bilet durum dağılımı. */
export const getStatusDistribution = () => api.get('/metrics/status-distribution');

/** Agent performans leaderboard. */
export const getAgentPerformance = () => api.get('/metrics/agent-performance');

/** Priority bazlı SLA metrikleri. */
export const getPrioritySlaMetrics = (days) =>
  api.get('/metrics/priority-sla-metrics', { params: days != null ? { days } : undefined });

/** Ürün bazında bilet metrikleri. */
export const getProductMetrics = (days) =>
  api.get('/metrics/product-metrics', { params: days != null ? { days } : undefined });

/** CSAT detaylı analitik metrikleri. */
export const getCsatMetrics = (months = 3) =>
  api.get('/metrics/csat-metrics', { params: { months } });

/** SLA breach uyarıları ve backlog metrikleri. */
export const getAlertsBacklog = () => api.get('/metrics/alerts-backlog');

/** Worklog özeti ve bilet tamamlanma metrikleri. */
export const getWorklogCompletion = (days = 30) =>
  api.get('/metrics/worklog-completion', { params: { days } });
