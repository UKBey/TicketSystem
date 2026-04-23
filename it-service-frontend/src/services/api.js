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

export default api;
