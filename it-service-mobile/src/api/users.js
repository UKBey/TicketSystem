import api from './client';

/** Kullanıcı detayını getirir (kendi profilin için user.id ile). */
export const getUser = (id) => api.get(`/users/${id}`);

/** Profil bilgilerini günceller. body: { firstName, lastName, email }. */
export const updateProfile = (body) => api.put('/users/me', body);

/** Şifre değiştirir. body: { currentPassword, newPassword }. */
export const changePassword = (body) => api.post('/users/me/password', body);

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
