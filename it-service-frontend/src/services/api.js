import axios from 'axios';
import keycloak, { redirectToKeycloakLogin } from '../keycloak';
import i18n from '../i18n';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

let loginRedirectInProgress = false;

function redirectToLoginOnce() {
  if (loginRedirectInProgress) return;
  loginRedirectInProgress = true;

  redirectToKeycloakLogin({ redirectUri: window.location.href }).catch(() => {
    // If redirect fails, release the guard so the user can retry manually.
    loginRedirectInProgress = false;
  });
}

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
  async (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        const originalRequest = error.config;

        // Retry once after a silent token refresh before redirecting.
        if (originalRequest && !originalRequest._retry401) {
          originalRequest._retry401 = true;
          try {
            await keycloak.updateToken(30);
            if (keycloak.token) {
              originalRequest.headers = {
                ...(originalRequest.headers || {}),
                Authorization: `Bearer ${keycloak.token}`,
              };
            }
            return api(originalRequest);
          } catch {
            // Fall through to login redirect.
          }
        }

        redirectToLoginOnce();
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

export const unclaimTicket = (ticketId, payload) =>
  api.delete(`/tickets/${ticketId}/claim`, { data: payload });

export const closeTicket = (ticketId, payload) =>
  api.put(`/tickets/${ticketId}/close`, payload);

export const updateTicketPriority = (ticketId, payload) =>
  api.put(`/tickets/${ticketId}/priority`, payload);

export const updateTicketTopic = (ticketId, payload) =>
  api.put(`/tickets/${ticketId}/topic`, payload);

export const listProductTopics = (productId) =>
  api.get(`/products/${productId}/topics`);

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

// SLA Policy API Functions
// ============================================================
// AI Summary API Functions (llm-service — /api/ai/*)
// ============================================================

// Ticket için yeni özet oluştur (AGENT / AGENT_ADMIN only)
export const generateAiSummary = (ticketId, language = 'tr') =>
  api.post(`/ai/summaries/tickets/${ticketId}/generate`, null, { params: { language } });

// Ticket'ın en son özetini getir
export const getLatestAiSummary = (ticketId) =>
  api.get(`/ai/summaries/tickets/${ticketId}/latest`);

// Ticket'ın tüm özetlerini listele
export const getAllAiSummaries = (ticketId) =>
  api.get(`/ai/summaries/tickets/${ticketId}`);

// ============================================================
// Known Issues (Sıkça Karşılaşılan Sorunlar) API
// ============================================================

export const listKnownIssues = (productId, { topicId, includeInactive = false } = {}) => {
  const params = {};
  if (topicId)         params.topicId = topicId;
  if (includeInactive) params.includeInactive = true;
  return api.get(`/products/${productId}/known-issues`, { params });
};

export const createKnownIssue = (productId, body) =>
  api.post(`/products/${productId}/known-issues`, body);

export const updateKnownIssue = (id, body) =>
  api.put(`/known-issues/${id}`, body);

export const deleteKnownIssue = (id) =>
  api.delete(`/known-issues/${id}`);
