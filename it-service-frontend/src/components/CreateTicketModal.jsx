import { useState, useEffect } from 'react';
import { X, Lightbulb, ChevronDown, ChevronUp } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api, { listKnownIssues } from '../services/api';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';

// Sentinel value for the "No Topic" choice — only offered when the selected
// product has no active topics. Submitted to the API as topicId: null.
const NO_TOPIC = 'NONE';

// Mirrors the backend constraint (tickets.title VARCHAR(500) + @Size(max=500)).
const TITLE_MAX = 500;

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

  // Known issues for the selected topic — informational, doesn't block submit.
  const [knownIssues, setKnownIssues] = useState([]);
  const [knownIssuesLoading, setKnownIssuesLoading] = useState(false);
  const [expandedIssueId, setExpandedIssueId] = useState(null);

  useEffect(() => {
    if (isOpen) {
      api.get('/products').then((res) => setProducts(sortByLocalizedName(res.data))).catch(() => {});
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
      .then((res) => setTopics(sortByLocalizedName(res.data)))
      .catch(() => setTopics([]))
      .finally(() => setTopicsLoading(false));
  }, [productId]);

  // Topic seçilince o topic için aktif known issue'ları çek — sadece bilgilendirme amaçlı.
  // "No Topic" seçildiyse ürün geneli known issue'lar çekilir (topicId gönderilmez).
  useEffect(() => {
    if (!productId || !topicId) {
      setKnownIssues([]);
      setExpandedIssueId(null);
      return;
    }
    setKnownIssuesLoading(true);
    setExpandedIssueId(null);
    const opts = topicId === NO_TOPIC ? {} : { topicId };
    listKnownIssues(productId, opts)
      .then((res) => setKnownIssues((res.data || []).filter((k) => k.isActive)))
      .catch(() => setKnownIssues([]))
      .finally(() => setKnownIssuesLoading(false));
  }, [productId, topicId]);

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
        topicId: topicId === NO_TOPIC ? null : Number(topicId),
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
    setKnownIssues([]);
    setExpandedIssueId(null);
    setError('');
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={onClose}>
      <div
        className="w-full max-w-md sm:max-w-lg md:max-w-2xl rounded-xl border animate-slide-up flex flex-col max-h-[90vh]"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 sm:px-6 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
          <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>{t('ticket.createModal.title')}</h3>
          <button onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col min-h-0 flex-1">
          {/* Body */}
          <div className="px-4 sm:px-6 py-5 space-y-4 overflow-y-auto flex-1">
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
                maxLength={TITLE_MAX}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
              />
              <p className="mt-1 text-right text-xs" style={{ color: title.length >= TITLE_MAX ? '#ef4444' : 'var(--text-tertiary)' }}>
                {title.length}/{TITLE_MAX}
              </p>
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
                  <option key={p.id} value={p.id}>{localizedName(p)}</option>
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
                {/* Product has no active topics → allow opening a topicless ticket. */}
                {productId && !topicsLoading && topics.length === 0 && (
                  <option value={NO_TOPIC}>{t('ticket.createModal.noTopicOption')}</option>
                )}
                {topics.map((tp) => (
                  <option key={tp.id} value={tp.id}>{localizedName(tp)}</option>
                ))}
              </select>
            </div>

            {/* Known issues — informational panel for the selected topic. */}
            {topicId && !knownIssuesLoading && knownIssues.length > 0 && (
              <div
                className="rounded-lg border p-3 animate-fade-in"
                style={{
                  backgroundColor: 'rgba(245, 158, 11, 0.06)',
                  borderColor: 'rgba(245, 158, 11, 0.3)',
                }}
              >
                <div className="flex items-start gap-2.5 mb-2.5">
                  <div
                    className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md"
                    style={{ backgroundColor: 'rgba(245, 158, 11, 0.15)' }}
                  >
                    <Lightbulb className="h-4 w-4" style={{ color: '#f59e0b' }} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>
                      {t('ticket.createModal.knownIssuesHeading')}
                    </p>
                    <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                      {t('ticket.createModal.knownIssuesSubtitle')}
                    </p>
                    <p className="mt-1 text-[11px] font-semibold uppercase tracking-wide" style={{ color: '#f59e0b' }}>
                      {t('ticket.createModal.knownIssuesCount', { count: knownIssues.length })}
                    </p>
                  </div>
                </div>

                <ul className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
                  {knownIssues.map((ki) => {
                    const isOpen = expandedIssueId === ki.id;
                    return (
                      <li
                        key={ki.id}
                        className="rounded-md border transition-colors"
                        style={{
                          backgroundColor: 'var(--bg-surface)',
                          borderColor: 'var(--border-color)',
                        }}
                      >
                        <button
                          type="button"
                          onClick={() => setExpandedIssueId(isOpen ? null : ki.id)}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left cursor-pointer"
                          aria-expanded={isOpen}
                          title={isOpen ? t('ticket.createModal.knownIssuesCollapse') : t('ticket.createModal.knownIssuesExpand')}
                        >
                          <span
                            className="flex-1 min-w-0 text-sm font-semibold break-words"
                            style={{ color: 'var(--text-primary)' }}
                          >
                            {ki.title}
                          </span>
                          {isOpen
                            ? <ChevronUp   className="h-4 w-4 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />
                            : <ChevronDown className="h-4 w-4 flex-shrink-0" style={{ color: 'var(--text-tertiary)' }} />}
                        </button>
                        {isOpen && ki.content && (
                          <div
                            className="px-3 pb-3 pt-0 text-sm whitespace-pre-wrap leading-relaxed border-t break-words"
                            style={{
                              color: 'var(--text-secondary)',
                              borderColor: 'var(--border-color)',
                              paddingTop: '0.5rem',
                            }}
                          >
                            {ki.content}
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-4 sm:px-6 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer w-full sm:w-auto"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
            >
              {t('form.cancel')}
            </button>
            <button
              type="submit"
              disabled={loading}
              className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer w-full sm:w-auto"
            >
              {loading ? t('ticket.createModal.creating') : t('ticket.createModal.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
