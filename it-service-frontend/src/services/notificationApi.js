import api from './api';

export const getNotifications = (page = 0, size = 20) =>
  api.get('/notifications', { params: { page, size } });

export const getUnreadCount = () =>
  api.get('/notifications/unread-count');

export const markAsRead = (id) =>
  api.patch(`/notifications/${id}/read`);

export const markAllAsRead = () =>
  api.post('/notifications/read-all');

export const deleteNotification = (id) =>
  api.delete(`/notifications/${id}`);

export const deleteAllNotifications = () =>
  api.delete('/notifications');

export const getPreferences = () =>
  api.get('/notification-preferences');

export const updatePreferences = (data) =>
  api.put('/notification-preferences', data);
