import { useState, useCallback, useEffect } from 'react';
import { getUnreadCount, markAsRead as apiMarkAsRead, markAllAsRead as apiMarkAllAsRead } from '../services/notificationApi';
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

  // Mount'ta hemen çek.
  useEffect(() => { fetchCount(); }, [fetchCount]);

  // Sonra 60 saniyede bir yenile.
  usePolling(fetchCount, 60_000);

  const markAsRead = useCallback(async (id) => {
    await apiMarkAsRead(id);
    fetchCount();
  }, [fetchCount]);

  const markAllAsRead = useCallback(async () => {
    await apiMarkAllAsRead();
    setUnreadCount(0);
  }, []);

  return { unreadCount, error, markAsRead, markAllAsRead, refresh: fetchCount };
}
