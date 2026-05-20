import api from './client';

/** Kullanıcı detayını getirir (kendi profilin için user.id ile). */
export const getUser = (id) => api.get(`/users/${id}`);

/** Profil bilgilerini günceller. body: { firstName, lastName, email }. */
export const updateProfile = (body) => api.put('/users/me', body);

/** Şifre değiştirir. body: { currentPassword, newPassword }. */
export const changePassword = (body) => api.post('/users/me/password', body);

/** Tercih edilen dili backend'e kaydeder — bildirim ve e-postalar bu değeri kullanır. */
export const updateLanguagePreference = (lang) =>
  api.put('/users/me/language', null, { params: { lang } });

// ---- Kullanıcı yönetimi (AGENT_ADMIN / MANAGER) ----

/** Sayfalı kullanıcı listesi. params: { page, size, search }. */
export const getUsers = (params) => api.get('/users', { params });

/** Yeni kullanıcı oluşturur. body: { username, email, firstName, lastName, password, roles }. */
export const createUser = (body) => api.post('/users/admin/create', body);

/** Atanabilir rolleri listeler. */
export const getAssignableRoles = () => api.get('/users/admin/roles');

/** Kullanıcının rollerini günceller (body: string[]). */
export const updateUserRoles = (id, roles) => api.put(`/users/${id}/roles`, roles);

/** Kullanıcıyı aktif/pasif yapar. */
export const updateUserStatus = (id, active) =>
  api.put(`/users/${id}/status`, null, { params: { active } });

/** Bir ürün için agent'ları kapasite bilgisiyle listeler (atama için). */
export const getAgentsWithCapacity = (productId) =>
  api.get('/users/agents/capacity', { params: { productId } });

// ---- Yönetim paneli — ürün yetkisi & agent limitleri ----

/** Kullanıcıya ürün yetkisi ekler. Güncellenmiş kullanıcıyı döner. */
export const assignProduct = (userId, productId) =>
  api.post(`/users/${userId}/products/${productId}`);

/** Kullanıcıdan ürün yetkisini kaldırır. Güncellenmiş kullanıcıyı döner. */
export const removeProduct = (userId, productId) =>
  api.delete(`/users/${userId}/products/${productId}`);

/** Agent'ın ürün bazlı bilet limiti override'larını listeler. */
export const getAgentLimits = (agentId) => api.get(`/agents/${agentId}/limits`);

/** Agent'ın bir ürün için bilet limitini ayarlar. body: { useCustomLimit, maxActiveTickets }. */
export const setAgentLimit = (agentId, productId, useCustomLimit, maxActiveTickets) =>
  api.put(`/agents/${agentId}/limits/${productId}`, { useCustomLimit, maxActiveTickets });

/** Agent'ın bir ürün için limit override'ını kaldırır. */
export const deleteAgentLimit = (agentId, productId) =>
  api.delete(`/agents/${agentId}/limits/${productId}`);
