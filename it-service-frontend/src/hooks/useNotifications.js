import { useState, useCallback } from 'react';
import {
  getUnreadCount,
  markAsRead as apiMarkAsRead,
  markAllAsRead as apiMarkAllAsRead,
  deleteNotification as apiDeleteNotification,
  deleteAllNotifications as apiDeleteAll,
} from '../services/notificationApi';
import { usePolling } from './usePolling';

export function useNotifications() {
  const [unreadCount, setUnreadCount] = useState(0);
  const [error, setError] = useState(null);

  const fetchCount = useCallback(async () => {
    try {
      const res = await getUnreadCount();
      setUnreadCount(res.data.count ?? 0);
      setError(null);
    } catch (err) {
      setError(err);
    }
  }, []);

  usePolling(fetchCount, 60_000, true);

  const markAsRead = useCallback(async (id) => {
    await apiMarkAsRead(id);
    fetchCount();
  }, [fetchCount]);

  const markAllAsRead = useCallback(async () => {
    await apiMarkAllAsRead();
    setUnreadCount(0);
  }, []);

  const deleteNotification = useCallback(async (id) => {
    await apiDeleteNotification(id);
    fetchCount();
  }, [fetchCount]);

  const deleteAllNotifications = useCallback(async () => {
    await apiDeleteAll();
    setUnreadCount(0);
  }, []);

  return {
    unreadCount,
    error,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    deleteAllNotifications,
    refresh: fetchCount,
  };
}
