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

export default api;

// Rate Limit API Functions
export const getRateLimitConfigs = () =>
  api.get('/admin/rate-limits');

export const updateRateLimitConfig = (id, { maxRequests, durationSeconds, enabled }) =>
  api.put(`/admin/rate-limits/${id}`, { maxRequests, durationSeconds, enabled });
