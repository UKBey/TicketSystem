import api from './client';

/** Sayfalı bildirim listesi. */
export const getNotifications = (page = 0, size = 50) =>
  api.get('/notifications', { params: { page, size } });

/** Okunmamış bildirim sayısı. */
export const getUnreadCount = () => api.get('/notifications/unread-count');

/** Tek bildirimi okundu işaretler. */
export const markAsRead = (id) => api.patch(`/notifications/${id}/read`);

/** Tüm bildirimleri okundu işaretler. */
export const markAllAsRead = () => api.post('/notifications/read-all');

/** Bir bildirimi siler. */
export const deleteNotification = (id) => api.delete(`/notifications/${id}`);

/** Kullanıcının tüm bildirimlerini kalıcı olarak siler. */
export const deleteAllNotifications = () => api.delete('/notifications');

/** Bildirim tercihlerini getirir. */
export const getPreferences = () => api.get('/notification-preferences');

/** Bildirim tercihlerini günceller. */
export const updatePreferences = (data) => api.put('/notification-preferences', data);
