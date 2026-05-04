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

export default {
  getDashboardSummary,
  getStatusDistribution,
  getAgentPerformance,
  getTicketTimeline,
  getPrioritySLAMetrics,
};