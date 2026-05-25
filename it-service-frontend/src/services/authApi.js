import axios from 'axios';
import i18n from '../i18n';

// Standalone axios instance for anonymous auth endpoints (forgot-password,
// reset-password). The default `api` instance intercepts 401s and redirects
// to Keycloak login — that's wrong for password-reset pages, which must
// stay accessible without a session.
const authApi = axios.create({
  baseURL: '/api/v1/auth',
  headers: {
    'Content-Type': 'application/json',
  },
});

authApi.interceptors.request.use(
  (config) => {
    config.headers['Accept-Language'] = i18n.language || 'en';
    return config;
  },
  (error) => Promise.reject(error)
);

export async function requestPasswordReset(email, { language, theme } = {}) {
  const payload = { email };
  if (language) payload.language = language;
  if (theme) payload.theme = theme;
  const response = await authApi.post('/forgot-password', payload);
  return response.data;
}

export async function validateResetToken(token) {
  const response = await authApi.get('/reset-password/validate', { params: { token } });
  return response.data.valid === true;
}

export async function resetPassword(token, newPassword, { language, theme } = {}) {
  const payload = { token, newPassword };
  if (language) payload.language = language;
  if (theme) payload.theme = theme;
  const response = await authApi.post('/reset-password', payload);
  return response.data;
}

export default authApi;
