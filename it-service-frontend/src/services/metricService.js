import api from './api';

async function getDashboardSummary() {
  const response = await api.get('/metrics/dashboard-summary');
  return response.data;
}

export default {
  getDashboardSummary,
};