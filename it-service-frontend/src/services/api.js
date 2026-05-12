import axios from 'axios';
import keycloak from '../keycloak';
import i18n from '../i18n';

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
    config.headers['Accept-Language'] = i18n.language || 'en';
    return config;
  },
  (error) => Promise.reject(error)
);

// Oturum suresi doldugunda 401 yakalanir ve kullanici tekrar girise yonlendirilir.
// 429 Too Many Requests durumunda global event firlatilir.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        keycloak.login();
      } else if (error.response.status === 429) {
        const retryAfter =
          error.response.data?.retryAfterSeconds ??
          parseInt(error.response.headers['retry-after'] ?? '60', 10);

        window.dispatchEvent(
          new CustomEvent('rate-limit-exceeded', { detail: { retryAfter } })
        );
      }
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

// Agent kapasite listesini çek (atama UI'ı için)
export const getAgentsWithCapacity = (productId) =>
  api.get('/users/agents/capacity', { params: { productId } });

// Bileti agent'a ata (Agent Admin)
export const assignTicket = (ticketId, targetAgentId, note) =>
  api.put(`/tickets/${ticketId}/assign`, { targetAgentId, note });

// ============================================================
// User Management API Functions (AGENT_ADMIN only)
// ============================================================

// Keycloak'ta yeni kullanıcı oluştur, geçici şifre ata ve rolleri eşle.
// Başarılı yanıt: 201 Created + UserCreationResponseDTO
export const createUser = (userData) =>
  api.post('/users/admin/create', userData);

// Realm'deki atanabilir rolleri listele (sistem rolleri filtrelenmiş).
// Başarılı yanıt: 200 OK + string[]
export const getAssignableRoles = () =>
  api.get('/users/admin/roles');

// Kullanıcının rollerini güncelle (AGENT_ADMIN only).
// Başarılı yanıt: 200 OK + UserDTO
export const updateUserRoles = (userId, roles) =>
  api.put(`/users/${userId}/roles`, roles);

// Kullanıcıyı deaktive et veya reaktive et (AGENT_ADMIN only).
// active=true → reaktive, active=false → soft-delete (Keycloak disabled + is_active=false)
export const updateUserStatus = (userId, active) =>
  api.put(`/users/${userId}/status`, null, { params: { active } });

export default api;

// Rate Limit API Functions
export const getRateLimitConfigs = () =>
  api.get('/admin/rate-limits');

export const updateRateLimitConfig = (id, { maxRequests, durationSeconds, enabled }) =>
  api.put(`/admin/rate-limits/${id}`, { maxRequests, durationSeconds, enabled });

// SLA Policy API Functions
export const getSlaPolicies = () =>
  api.get('/admin/sla-policies');

export const updateSlaPolicy = (id, { targetResolutionHours, warningThresholdHours }) =>
  api.put(`/admin/sla-policies/${id}`, { targetResolutionHours, warningThresholdHours });
