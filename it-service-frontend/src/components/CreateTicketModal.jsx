import { useState, useEffect } from 'react';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';

export default function CreateTicketModal({ isOpen, onClose, onCreated }) {
  const { t } = useTranslation();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [productId, setProductId] = useState('');
  const [products, setProducts] = useState([]);
  const [topicId, setTopicId] = useState('');
  const [topics, setTopics] = useState([]);
  const [topicsLoading, setTopicsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      api.get('/products').then((res) => setProducts(res.data)).catch(() => {});
    }
  }, [isOpen]);

  useEffect(() => {
    if (!productId) {
      setTopics([]);
      setTopicId('');
      return;
    }
    setTopicsLoading(true);
    setTopicId('');
    api
      .get(`/products/${productId}/topics`)
      .then((res) => setTopics(res.data))
      .catch(() => setTopics([]))
      .finally(() => setTopicsLoading(false));
  }, [productId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim() || !productId || !topicId) {
      setError(t('ticket.createModal.errorRequired'));
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await api.post('/tickets', {
        title,
        description,
        priority,
        productId: Number(productId),
        topicId: Number(topicId),
      });
      onCreated(res.data);
      resetForm();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || t('ticket.createModal.errorCreate'));
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setTitle('');
    setDescription('');
    setPriority('MEDIUM');
    setProductId('');
    setTopicId('');
    setTopics([]);
    setError('');
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={onClose}>
      <div
        className="w-full max-w-lg rounded-xl border animate-slide-up"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.title')}</h3>
          <button onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Body */}
          <div className="px-6 py-5 space-y-4">
            {error && (
              <div className="rounded-lg px-3 py-2 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
                {error}
              </div>
            )}
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.labelTitle')} *</label>
              <input
                type="text"
                placeholder={t('ticket.createModal.placeholderTitle')}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              />
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.labelDescription')}</label>
              <textarea
                placeholder={t('ticket.createModal.placeholderDescription')}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 resize-y min-h-[80px]"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              />
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.labelPriority')}</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              >
                <option value="LOW">{t('ticket.priority.low')}</option>
                <option value="MEDIUM">{t('ticket.priority.medium')}</option>
                <option value="HIGH">{t('ticket.priority.high')}</option>
                <option value="CRITICAL">{t('ticket.priority.critical')}</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.labelProduct')} *</label>
              <select
                value={productId}
                onChange={(e) => setProductId(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              >
                <option value="">{t('ticket.createModal.selectProduct')}</option>
                {products.filter((p) => p.isActive).map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.labelTopic')} *</label>
              <select
                value={topicId}
                onChange={(e) => setTopicId(e.target.value)}
                disabled={!productId || topicsLoading}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2 cursor-pointer disabled:cursor-not-allowed disabled:opacity-60"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              >
                <option value="">
                  {!productId
                    ? t('ticket.createModal.selectProductFirst')
                    : topicsLoading
                    ? t('ticket.createModal.topicsLoading')
                    : topics.length === 0
                    ? t('ticket.createModal.noTopics')
                    : t('ticket.createModal.selectTopic')}
                </option>
                {topics.map((tp) => (
                  <option key={tp.id} value={tp.id}>{tp.name}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Footer */}
          <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
            >
              {t('form.cancel')}
            </button>
            <button
              type="submit"
              disabled={loading}
              className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {loading ? t('ticket.createModal.creating') : t('ticket.createModal.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
