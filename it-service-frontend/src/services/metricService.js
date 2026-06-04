import api from './api';

async function getDashboardSummary() {
  const response = await api.get('/metrics/dashboard-summary');
  return response.data;
}

async function getStatusDistribution() {
  const response = await api.get('/metrics/status-distribution');
  return response.data;
}

async function getAgentPerformance() {
  const response = await api.get('/metrics/agent-performance');
  return response.data;
}

async function getTicketTimeline(days = 30) {
  const response = await api.get('/metrics/ticket-timeline', {
    params: { days },
  });
  return response.data;
}

async function getPrioritySLAMetrics(days) {
  const params = days != null ? { days } : undefined;
  const response = await api.get('/metrics/priority-sla-metrics', { params });
  return response.data;
}

async function getProductMetrics(days) {
  const params = days != null ? { days } : undefined;
  const response = await api.get('/metrics/product-metrics', { params });
  return response.data;
}

async function getCSATMetrics(months = 3) {
  const response = await api.get('/metrics/csat-metrics', { params: { months } });
  return response.data;
}

async function getAlertsAndBacklog() {
  const response = await api.get('/metrics/alerts-backlog');
  return response.data;
}

async function getWorklogCompletion(days = 30) {
  const response = await api.get('/metrics/worklog-completion', { params: { days } });
  return response.data;
}

// Kişisel dashboard'lar — self-scoped (JWT subject). Müşteri kendi açtığı,
// ajan kendi claim'lediği biletler üzerinden metrik görür.
async function getMyCustomerDashboard(days = 30) {
  const response = await api.get('/metrics/me/customer', { params: { days } });
  return response.data;
}

async function getMyAgentDashboard(days = 30) {
  const response = await api.get('/metrics/me/agent', { params: { days } });
  return response.data;
}

// Oversight — başka bir kullanıcının dashboard'u. ADMIN/MANAGER global görür,
// LEAD_AGENT yalnızca kendi ürünlerindeki veriyi görür (backend kapsamı uygular).
async function getUserAgentDashboard(userId, days = 30) {
  const response = await api.get(`/metrics/users/${userId}/agent`, { params: { days } });
  return response.data;
}

async function getUserCustomerDashboard(userId, days = 30) {
  const response = await api.get(`/metrics/users/${userId}/customer`, { params: { days } });
  return response.data;
}

export default {
  getDashboardSummary,
  getStatusDistribution,
  getAgentPerformance,
  getTicketTimeline,
  getPrioritySLAMetrics,
  getProductMetrics,
  getCSATMetrics,
  getAlertsAndBacklog,
  getWorklogCompletion,
  getMyCustomerDashboard,
  getMyAgentDashboard,
  getUserAgentDashboard,
  getUserCustomerDashboard,
};