import axios from 'axios';
import keycloak from '../keycloak';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Her istekte varsa guncel Keycloak token'ini Authorization header'ina ekler.
api.interceptors.request.use(
  (config) => {
    if (keycloak.token) {
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Oturum suresi doldugunda 401 yakalanir ve kullanici tekrar girise yonlendirilir.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      keycloak.login();
    }
    return Promise.reject(error);
  }
);

/**
 * Product limit management functions
 */
export const updateProductLimit = (productId, maxActiveTickets) =>
  api.patch(`/products/${productId}/limit`, { maxActiveTickets });

export const getAgentLimits = (agentId) =>
  api.get(`/agents/${agentId}/limits`);

export const setAgentLimit = (agentId, productId, useCustomLimit, maxActiveTickets) =>
  api.put(`/agents/${agentId}/limits/${productId}`, {
    useCustomLimit,
    maxActiveTickets
  });

export const deleteAgentLimit = (agentId, productId) =>
  api.delete(`/agents/${agentId}/limits/${productId}`);

export const unclaimTicket = (ticketId, note) =>
  api.delete(`/tickets/${ticketId}/claim`, { data: { note } });

export const closeTicket = (ticketId, note) =>
  api.put(`/tickets/${ticketId}/close`, { note });

export default api;
