import api from './client';

/** Kullanıcının yetkili olduğu ürünler. */
export const getProducts = () => api.get('/products');

/** Bir ürünün talep konuları (topic). */
export const getProductTopics = (productId) => api.get(`/products/${productId}/topics`);

/** Bir ürün/konu için aktif "bilinen sorunlar". */
export const getKnownIssues = (productId, topicId) =>
  api.get(`/products/${productId}/known-issues${topicId ? `?topicId=${topicId}` : ''}`);
