import api from './client';

/** Kullanıcının yetkili olduğu ürünler. */
export const getProducts = () => api.get('/products');

/** Bir ürünün talep konuları (topic). */
export const getProductTopics = (productId) => api.get(`/products/${productId}/topics`);

/** Bir ürün/konu için aktif "bilinen sorunlar". */
export const getKnownIssues = (productId, topicId) =>
  api.get(`/products/${productId}/known-issues${topicId ? `?topicId=${topicId}` : ''}`);

/** Yeni bilinen sorun oluşturur (yalnızca AGENT_ADMIN / MANAGER). */
export const createKnownIssue = (productId, body) =>
  api.post(`/products/${productId}/known-issues`, body);

/** Bilinen sorun kaydını siler (yalnızca AGENT_ADMIN / MANAGER). */
export const deleteKnownIssue = (id) => api.delete(`/known-issues/${id}`);
