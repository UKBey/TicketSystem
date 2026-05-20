import api from './client';

/** Kullanıcının yetkili olduğu ürünler. */
export const getProducts = () => api.get('/products');

/** Tek ürün detayı. */
export const getProduct = (id) => api.get(`/products/${id}`);

/** Yeni ürün oluşturur (AGENT_ADMIN / MANAGER). body: { name, isActive, maxActiveTickets }. */
export const createProduct = (body) => api.post('/products', body);

/** Ürünü günceller (AGENT_ADMIN / MANAGER). */
export const updateProduct = (id, body) => api.put(`/products/${id}`, body);

/** Ürünü siler (AGENT_ADMIN / MANAGER). */
export const deleteProduct = (id) => api.delete(`/products/${id}`);

/** Bir ürünün talep konuları (topic). includeInactive: admin görünümü için pasifleri de getirir. */
export const getProductTopics = (productId, includeInactive = false) =>
  api.get(`/products/${productId}/topics`, {
    params: includeInactive ? { includeInactive: true } : undefined,
  });

/** Bir ürüne talep konusu ekler (AGENT_ADMIN / MANAGER). body: { name, isActive }. */
export const createTopic = (productId, body) =>
  api.post(`/products/${productId}/topics`, body);

/** Talep konusunu günceller (AGENT_ADMIN / MANAGER). */
export const updateTopic = (id, body) => api.put(`/topics/${id}`, body);

/** Talep konusunu siler (AGENT_ADMIN / MANAGER). */
export const deleteTopic = (id) => api.delete(`/topics/${id}`);

/** Bir ürün/konu için aktif "bilinen sorunlar". */
export const getKnownIssues = (productId, topicId) =>
  api.get(`/products/${productId}/known-issues${topicId ? `?topicId=${topicId}` : ''}`);

/** Yeni bilinen sorun oluşturur (yalnızca AGENT_ADMIN / MANAGER). */
export const createKnownIssue = (productId, body) =>
  api.post(`/products/${productId}/known-issues`, body);

/** Bilinen sorun kaydını siler (yalnızca AGENT_ADMIN / MANAGER). */
export const deleteKnownIssue = (id) => api.delete(`/known-issues/${id}`);
