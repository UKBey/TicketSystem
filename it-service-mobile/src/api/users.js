import api from './client';

/** Kullanıcı detayını getirir (kendi profilin için user.id ile). */
export const getUser = (id) => api.get(`/users/${id}`);

/** Profil bilgilerini günceller. body: { firstName, lastName, email }. */
export const updateProfile = (body) => api.put('/users/me', body);

/** Şifre değiştirir. body: { currentPassword, newPassword }. */
export const changePassword = (body) => api.post('/users/me/password', body);
