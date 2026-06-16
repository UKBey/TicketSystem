import { useState, useEffect, useRef } from 'react';
import { Bell } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNotifications } from '../../hooks/useNotifications';
import { NotificationContext } from './NotificationContext';
import NotificationList from './NotificationList';

export default function NotificationBell() {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  // Tek useNotifications() örneği; NotificationList aynı state'i context'ten
  // tüketir, böylece listede okundu/sil işlemleri rozeti anında günceller.
  const notifications = useNotifications();
  const { unreadCount, markAllAsRead } = notifications;
  const containerRef = useRef(null);

  // Dışarı tıklayınca kapat.
  useEffect(() => {
    if (!isOpen) return;
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const handleMarkAllRead = async () => {
    await markAllAsRead();
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setIsOpen((prev) => !prev)}
        className="relative flex h-9 w-9 items-center justify-center rounded-lg transition-colors cursor-pointer"
        style={{
          backgroundColor: 'var(--bg-surface-secondary)',
          color: 'var(--text-secondary)',
        }}
        aria-label={t('notification.title')}
      >
        <Bell className="h-[18px] w-[18px]" />

        {unreadCount > 0 && (
          <span
            className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full text-[10px] font-bold text-white"
            style={{ backgroundColor: '#ef4444' }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <NotificationContext.Provider value={notifications}>
          <NotificationList onMarkAllRead={handleMarkAllRead} onClose={() => setIsOpen(false)} />
        </NotificationContext.Provider>
      )}
    </div>
  );
}
