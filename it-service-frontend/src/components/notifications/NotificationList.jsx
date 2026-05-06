import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getNotifications } from '../../services/notificationApi';

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const diff = Math.floor((Date.now() - new Date(dateStr)) / 1000);
  if (diff < 60) return 'Az önce';
  if (diff < 3600) return `${Math.floor(diff / 60)} dk önce`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} sa önce`;
  return new Date(dateStr).toLocaleDateString('tr-TR');
}

export default function NotificationList({ onMarkAllRead }) {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    getNotifications(0, 20)
      .then((res) => setNotifications(res.data.content ?? []))
      .catch(() => setNotifications([]))
      .finally(() => setLoading(false));
  }, []);

  const handleItemClick = (notification) => {
    if (notification.referenceType === 'TICKET' && notification.referenceId) {
      navigate(`/tickets/${notification.referenceId}`);
    }
  };

  return (
    <div
      className="absolute right-0 top-full mt-2 w-80 rounded-xl shadow-xl overflow-hidden z-50"
      style={{ backgroundColor: 'var(--bg-surface)', border: '1px solid var(--border-color)' }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-4 py-3 border-b"
        style={{ borderColor: 'var(--border-color)' }}
      >
        <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          Bildirimler
        </span>
        <button
          onClick={onMarkAllRead}
          className="text-xs transition-opacity hover:opacity-70 cursor-pointer"
          style={{ color: '#3b82f6', background: 'none', border: 'none' }}
        >
          Tümünü okundu işaretle
        </button>
      </div>

      {/* List */}
      <div className="max-h-80 overflow-y-auto">
        {loading && (
          <div className="flex items-center justify-center py-8">
            <div
              className="h-5 w-5 rounded-full border-2 animate-spin"
              style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
            />
          </div>
        )}

        {!loading && notifications.length === 0 && (
          <div className="py-10 text-center text-sm" style={{ color: 'var(--text-tertiary)' }}>
            Bildiriminiz bulunmuyor.
          </div>
        )}

        {!loading && notifications.map((n) => (
          <div
            key={n.id}
            onClick={() => handleItemClick(n)}
            className="flex flex-col gap-0.5 px-4 py-3 border-b transition-colors"
            style={{
              borderColor: 'var(--border-color)',
              backgroundColor: n.isRead ? 'transparent' : 'color-mix(in srgb, #3b82f6 8%, transparent)',
              cursor: n.referenceType === 'TICKET' ? 'pointer' : 'default',
            }}
          >
            <span className="text-sm leading-snug" style={{ color: 'var(--text-primary)' }}>
              {n.message}
            </span>
            <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
              {timeAgo(n.createdAt)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
