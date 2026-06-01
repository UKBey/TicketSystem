import api from './client';

/**
 * Hazır Yanıtlar (canned responses) API — yalnızca ajan/yönetici rolleri erişebilir.
 * Müşteriye INTERNAL şablonlar sızmaz (uçlar rol bazlı korunur).
 */

/** Görülebilir şablonları listele. params: { productId, scope, visibility, q }. */
export const getCannedResponses = (params = {}) =>
  api.get('/canned-responses', { params });

export const createCannedResponse = (body) => api.post('/canned-responses', body);

export const updateCannedResponse = (id, body) => api.put(`/canned-responses/${id}`, body);

export const deleteCannedResponse = (id) => api.delete(`/canned-responses/${id}`);

export const favoriteCannedResponse = (id) => api.post(`/canned-responses/${id}/favorite`);

export const unfavoriteCannedResponse = (id) => api.delete(`/canned-responses/${id}/favorite`);
