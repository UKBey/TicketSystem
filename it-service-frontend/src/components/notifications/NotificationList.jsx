import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Trash2 } from 'lucide-react';
import { getNotifications } from '../../services/notificationApi';
import { useNotifications } from '../../hooks/useNotifications';

function timeAgo(dateStr, t) {
  if (!dateStr) return '';
  const diff = Math.floor((Date.now() - new Date(dateStr)) / 1000);
  if (diff < 60)    return t('notification.justNow');
  if (diff < 3600)  return t('notification.minutesAgo', { count: Math.floor(diff / 60) });
  if (diff < 86400) return t('notification.hoursAgo',   { count: Math.floor(diff / 3600) });
  return new Date(dateStr).toLocaleDateString();
}

export default function NotificationList({ onMarkAllRead, onClose }) {
  const { t } = useTranslation();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [deletingAll, setDeletingAll] = useState(false);
  const navigate = useNavigate();
  const { deleteNotification, deleteAllNotifications } = useNotifications();

  const load = useCallback(() => {
    setLoading(true);
    getNotifications(0, 20)
      .then((res) => setNotifications(res.data.content ?? []))
      .catch(() => setNotifications([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleItemClick = (notification) => {
    if (notification.referenceType === 'TICKET' && notification.referenceId) {
      navigate(`/tickets/${notification.referenceId}`);
      onClose?.();
    }
  };

  const handleDelete = async (e, id) => {
    e.stopPropagation();
    await deleteNotification(id);
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  };

  const handleDeleteAll = async () => {
    setDeletingAll(true);
    try {
      await deleteAllNotifications();
      setNotifications([]);
    } finally {
      setDeletingAll(false);
    }
  };

  return (
    <div
      className="absolute right-0 top-full mt-2 w-80 max-w-[calc(100vw-1rem)] rounded-xl shadow-xl overflow-hidden z-50"
      style={{ backgroundColor: 'var(--bg-surface)', border: '1px solid var(--border-color)' }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-4 py-3 border-b"
        style={{ borderColor: 'var(--border-color)' }}
      >
        <span className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
          {t('notification.title')}
        </span>
        <div className="flex items-center gap-3">
          <button
            onClick={onMarkAllRead}
            className="text-xs transition-opacity hover:opacity-70 cursor-pointer"
            style={{ color: '#3b82f6', background: 'none', border: 'none' }}
          >
            {t('notification.markAllRead')}
          </button>
          {notifications.length > 0 && (
            <button
              onClick={handleDeleteAll}
              disabled={deletingAll}
              title={t('notification.deleteAll')}
              className="flex items-center justify-center rounded transition-colors cursor-pointer disabled:opacity-50"
              style={{ color: 'var(--text-tertiary)', background: 'none', border: 'none' }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#ef4444')}
              onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-tertiary)')}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
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
            {t('notification.empty')}
          </div>
        )}

        {!loading && notifications.map((n) => (
          <div
            key={n.id}
            onClick={() => handleItemClick(n)}
            className="group flex items-start gap-2 px-4 py-3 border-b transition-colors"
            style={{
              borderColor: 'var(--border-color)',
              backgroundColor: n.isRead ? 'transparent' : 'color-mix(in srgb, #3b82f6 8%, transparent)',
              cursor: n.referenceType === 'TICKET' ? 'pointer' : 'default',
            }}
          >
            <div className="flex flex-col gap-0.5 flex-1 min-w-0">
              <span className="text-sm leading-snug" style={{ color: 'var(--text-primary)' }}>
                {n.message}
              </span>
              <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                {timeAgo(n.createdAt, t)}
              </span>
            </div>
            {/* Tek bildirim silme butonu — hover'da görünür */}
            <button
              onClick={(e) => handleDelete(e, n.id)}
              title={t('notification.delete')}
              className="flex-shrink-0 mt-0.5 rounded p-0.5 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
              style={{ color: 'var(--text-tertiary)', background: 'none', border: 'none' }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#ef4444')}
              onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-tertiary)')}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
