import axios from 'axios';
import i18n from '../i18n';
import { API_BASE_URL } from '../config';

/**
 * Backend REST API istemcisi.
 *
 * Token, AuthContext tarafından `setAuthToken` ile güncellenir (web'de keycloak.token
 * neyse buradaki module-level holder o). 401 durumunda kayıtlı handler tetiklenir —
 * AuthContext token yenilemeyi / oturumu kapatmayı orada yönetir.
 */
let authToken = null;
let onUnauthorized = null;

export function setAuthToken(token) {
  authToken = token || null;
}

export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  config.headers['Accept-Language'] = i18n.language || 'en';
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof onUnauthorized === 'function') {
      onUnauthorized();
    }
    return Promise.reject(error);
  },
);

export default api;
