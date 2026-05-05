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

async function getPrioritySLAMetrics() {
  const response = await api.get('/metrics/priority-sla-metrics');
  return response.data;
}

async function getProductMetrics() {
  const response = await api.get('/metrics/product-metrics');
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

export default {
  getDashboardSummary,
  getStatusDistribution,
  getAgentPerformance,
  getTicketTimeline,
  getPrioritySLAMetrics,
  getProductMetrics,
  getCSATMetrics,
  getAlertsAndBacklog,
};