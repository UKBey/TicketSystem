import { createContext, useContext } from 'react';

// NotificationBell ile NotificationList'in AYNI useNotifications() örneğini
// paylaşması için context. Aksi halde her bileşen kendi unreadCount state'ini
// tutar ve listede okundu/sil işlemleri rozetin sayacını güncellemez (yalnızca
// 60 sn'lik poll veya F5 ile düzelir).
export const NotificationContext = createContext(null);

export function useNotificationContext() {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error('useNotificationContext must be used within a NotificationContext.Provider');
  }
  return ctx;
}
