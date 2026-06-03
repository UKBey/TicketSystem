import api from './client';

/** Kullanıcının yetkili olduğu ürünler. */
export const getProducts = () => api.get('/products');

/** Tek ürün detayı. */
export const getProduct = (id) => api.get(`/products/${id}`);

/** Yeni ürün oluşturur (ADMIN). body: { name, isActive, maxActiveTickets }. */
export const createProduct = (body) => api.post('/products', body);

/** Ürünü günceller (ADMIN). */
export const updateProduct = (id, body) => api.put(`/products/${id}`, body);

/** Ürünü siler (ADMIN). */
export const deleteProduct = (id) => api.delete(`/products/${id}`);

/** Bir ürünün talep konuları (topic). includeInactive: admin görünümü için pasifleri de getirir. */
export const getProductTopics = (productId, includeInactive = false) =>
  api.get(`/products/${productId}/topics`, {
    params: includeInactive ? { includeInactive: true } : undefined,
  });

/** Bir ürüne talep konusu ekler (LEAD_AGENT / ADMIN). body: { name, isActive }. */
export const createTopic = (productId, body) =>
  api.post(`/products/${productId}/topics`, body);

/** Talep konusunu günceller (LEAD_AGENT / ADMIN). */
export const updateTopic = (id, body) => api.put(`/topics/${id}`, body);

/** Talep konusunu siler (LEAD_AGENT / ADMIN). */
export const deleteTopic = (id) => api.delete(`/topics/${id}`);

/** Bir ürün/konu için aktif "bilinen sorunlar". */
export const getKnownIssues = (productId, topicId) =>
  api.get(`/products/${productId}/known-issues${topicId ? `?topicId=${topicId}` : ''}`);

/** Yeni bilinen sorun oluşturur (LEAD_AGENT / ADMIN). */
export const createKnownIssue = (productId, body) =>
  api.post(`/products/${productId}/known-issues`, body);

/** Bilinen sorun kaydını siler (LEAD_AGENT / ADMIN). */
export const deleteKnownIssue = (id) => api.delete(`/known-issues/${id}`);
