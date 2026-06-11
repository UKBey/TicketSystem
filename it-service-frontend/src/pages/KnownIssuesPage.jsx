import { useState, useEffect, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { LifeBuoy, Plus, Pencil, Trash2, X, Tag, Package, ChevronDown } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../services/api';
import {
  listKnownIssues,
  createKnownIssue,
  updateKnownIssue,
  deleteKnownIssue,
} from '../services/api';
import PaginationBar from '../components/PaginationBar';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';

/**
 * Sıkça Karşılaşılan Sorunlar sayfası.
 *
 * - Kullanıcı yetkili olduğu ürünleri görür; ürün seçince o ürüne ait kayıtlar listelenir.
 * - LEAD_AGENT / ADMIN kayıt ekleyebilir, düzenleyebilir, silebilir.
 * - Diğer roller sadece okur.
 * - Tasarım mobile-first: tek sütun kart düzeni, sm+ iki sütun.
 */
export default function KnownIssuesPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const { isLeadAgent, isAdmin } = useAuth();
  // Ürün içeriği (bilinen sorunlar) yönetimi — lead agent veya admin.
  const canManage = isLeadAgent || isAdmin;

  const [products, setProducts]                 = useState([]);
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [topics, setTopics]                     = useState([]);
  const [topicFilter, setTopicFilter]           = useState('');
  const [items, setItems]                       = useState([]);
  const [loading, setLoading]                   = useState(false);
  const [error, setError]                       = useState('');

  // Sayfalama — liste tek seferde çekildiği için istemci tarafında dilimlenir.
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing]         = useState(null);
  const [form, setForm]               = useState({ title: '', content: '', topicId: '', isActive: true });
  const [saving, setSaving]           = useState(false);

  // Akordiyon — birden fazla kayıt aynı anda açık kalabilir.
  const [expandedIds, setExpandedIds] = useState(() => new Set());
  const toggleExpanded = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  // ---------------------------------------------------------------
  // Ürün listesi — kullanıcının yetkili olduğu ürünler
  // ---------------------------------------------------------------
  useEffect(() => {
    api.get('/products')
      .then((res) => {
        setProducts(res.data);
        if (res.data.length > 0) setSelectedProductId(res.data[0].id);
      })
      .catch(() => setError(t('knownIssues.errorLoadProducts')));
  }, [t]);

  // ---------------------------------------------------------------
  // Seçili ürünün topic'leri (admin'in form'da seçebilmesi için)
  // ---------------------------------------------------------------
  useEffect(() => {
    if (!selectedProductId) {
      setTopics([]);
      return;
    }
    api.get(`/products/${selectedProductId}/topics`)
      .then((res) => setTopics(res.data))
      .catch(() => setTopics([]));
  }, [selectedProductId]);

  // ---------------------------------------------------------------
  // Listeleme
  // ---------------------------------------------------------------
  const fetchItems = useCallback(async () => {
    if (!selectedProductId) return;
    try {
      setLoading(true);
      setError('');
      const res = await listKnownIssues(selectedProductId, {
        topicId: topicFilter || undefined,
        includeInactive: canManage,
      });
      setItems(res.data);
    } catch (err) {
      console.error('Known issues yüklenemedi:', err);
      setError(t('knownIssues.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [selectedProductId, topicFilter, canManage, t]);

  useEffect(() => { fetchItems(); }, [fetchItems]);

  // ---------------------------------------------------------------
  // Sayfalama hesapları
  // ---------------------------------------------------------------
  const totalPages = Math.ceil(items.length / size);
  const paginated  = items.slice(page * size, page * size + size);

  // Ürün/topic filtresi değişince ilk sayfaya dön.
  useEffect(() => { setPage(0); }, [selectedProductId, topicFilter]);

  // Silme/azalma sonrası mevcut sayfa aralık dışında kalırsa son geçerli sayfaya çek.
  useEffect(() => {
    if (page > 0 && page >= totalPages) setPage(Math.max(0, totalPages - 1));
  }, [page, totalPages]);

  // ---------------------------------------------------------------
  // Modal akışı
  // ---------------------------------------------------------------
  const openCreate = () => {
    setEditing(null);
    setForm({ title: '', content: '', topicId: '', isActive: true });
    setIsModalOpen(true);
  };

  const openEdit = (item) => {
    setEditing(item);
    setForm({
      title: item.title ?? '',
      content: item.content ?? '',
      topicId: item.topicId ? String(item.topicId) : '',
      isActive: item.isActive ?? true,
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditing(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!form.title.trim() || !form.content.trim()) {
      toast.error(t('knownIssues.errorRequired'));
      return;
    }
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      topicId: form.topicId ? Number(form.topicId) : null,
      isActive: form.isActive,
    };
    try {
      setSaving(true);
      if (editing) {
        const res = await updateKnownIssue(editing.id, payload);
        setItems((prev) => prev.map((it) => (it.id === editing.id ? res.data : it)));
      } else {
        const res = await createKnownIssue(selectedProductId, payload);
        setItems((prev) => [res.data, ...prev]);
      }
      closeModal();
    } catch (err) {
      toast.error(err.response?.data?.message || t('knownIssues.errorSave'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    if (!window.confirm(t('knownIssues.confirmDelete', { title: item.title }))) return;
    try {
      await deleteKnownIssue(item.id);
      setItems((prev) => prev.filter((it) => it.id !== item.id));
    } catch (err) {
      toast.error(err.response?.data?.message || t('knownIssues.errorDelete'));
    }
  };

  // ---------------------------------------------------------------
  // Render helpers
  // ---------------------------------------------------------------
  const topicLookup = useMemo(() => {
    const map = {};
    topics.forEach((tp) => { map[tp.id] = localizedName(tp); });
    return map;
    // localizedName aktif dili global i18n'den okur — dil değişince yeniden hesaplanmalı.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topics, i18n.language]);

  return (
    <>
      {/* Page header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
          <LifeBuoy className="h-6 w-6 text-primary-500" />
          {t('knownIssues.title')}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          {t('knownIssues.subtitle')}
        </p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      {/* Toolbar — product picker + topic filter + add btn */}
      <div
        className="rounded-xl border p-4 mb-5 flex flex-col gap-2 sm:gap-3 sm:flex-row sm:items-end sm:justify-between"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
      >
        <div className="flex flex-col gap-2 sm:gap-3 sm:flex-row sm:items-end flex-1 min-w-0">
          <div className="w-full sm:w-auto sm:flex-1 sm:min-w-[12rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5 flex items-center gap-1.5" style={{ color: 'var(--text-tertiary)' }}>
              <Package className="h-3.5 w-3.5" />
              {t('knownIssues.productLabel')}
            </label>
            <select
              value={selectedProductId ?? ''}
              onChange={(e) => { setSelectedProductId(Number(e.target.value)); setTopicFilter(''); }}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              {products.length === 0 && <option value="">{t('knownIssues.noProducts')}</option>}
              {sortByLocalizedName(products).map((p) => (
                <option key={p.id} value={p.id}>{localizedName(p)}</option>
              ))}
            </select>
          </div>

          {topics.length > 0 && (
            <div className="w-full sm:w-auto sm:flex-1 sm:min-w-[12rem] min-w-0">
              <label className="block text-xs font-semibold mb-1.5 flex items-center gap-1.5" style={{ color: 'var(--text-tertiary)' }}>
                <Tag className="h-3.5 w-3.5" />
                {t('knownIssues.topicFilterLabel')}
              </label>
              <select
                value={topicFilter}
                onChange={(e) => setTopicFilter(e.target.value)}
                className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
              >
                <option value="">{t('knownIssues.allTopics')}</option>
                {sortByLocalizedName(topics).map((tp) => (
                  <option key={tp.id} value={tp.id}>{localizedName(tp)}</option>
                ))}
              </select>
            </div>
          )}
        </div>

        {canManage && selectedProductId && (
          <button
            onClick={openCreate}
            className="inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer w-full sm:w-auto"
          >
            <Plus className="h-4 w-4" />
            {t('knownIssues.add')}
          </button>
        )}
      </div>

      {/* Card grid */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-8 w-8 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
        </div>
      ) : items.length === 0 ? (
        <div
          className="rounded-xl border py-12 text-center text-sm"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
        >
          {selectedProductId ? t('knownIssues.empty') : t('knownIssues.noProducts')}
        </div>
      ) : (
        <div
          className="rounded-xl border overflow-hidden"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
        >
          {paginated.map((item, idx) => {
            const isOpen = expandedIds.has(item.id);
            return (
              <div
                key={item.id}
                className={idx > 0 ? 'border-t' : ''}
                style={{ borderColor: 'var(--border-color)' }}
              >
                {/* Header — tiklanabilir, accordion'i acar/kapar */}
                <button
                  type="button"
                  onClick={() => toggleExpanded(item.id)}
                  className="w-full flex items-center gap-3 px-4 sm:px-5 py-3.5 text-left transition-colors cursor-pointer hover:bg-[color:var(--bg-surface-hover)]"
                  aria-expanded={isOpen}
                >
                  <ChevronDown
                    className="h-4 w-4 shrink-0 transition-transform duration-200"
                    style={{
                      color: 'var(--text-tertiary)',
                      transform: isOpen ? 'rotate(0deg)' : 'rotate(-90deg)',
                    }}
                  />

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3 className="text-sm font-semibold break-words min-w-0" style={{ color: 'var(--text-primary)' }}>
                        {item.title}
                      </h3>
                      {!item.isActive && (
                        <span className="shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300">
                          {t('knownIssues.statusInactive')}
                        </span>
                      )}
                    </div>
                    {item.topicId && topicLookup[item.topicId] && (
                      <div className="mt-0.5 inline-flex items-center gap-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>
                        <Tag className="h-3 w-3" />
                        {topicLookup[item.topicId]}
                      </div>
                    )}
                  </div>
                </button>

                {/* Body — sadece acikken render edilir; uzun icerik diger satirlari etkilemez */}
                {isOpen && (
                  <div className="px-4 sm:px-5 pb-4 pl-11 sm:pl-12">
                    <p
                      className="text-sm whitespace-pre-wrap leading-relaxed break-words"
                      style={{ color: 'var(--text-secondary)' }}
                    >
                      {item.content}
                    </p>
                    {canManage && (
                      <div
                        className="mt-4 flex flex-wrap justify-end gap-2 border-t pt-3"
                        style={{ borderColor: 'var(--border-color)' }}
                      >
                        <button
                          onClick={() => openEdit(item)}
                          className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors cursor-pointer"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                        >
                          <Pencil className="h-3 w-3" />
                          {t('knownIssues.edit')}
                        </button>
                        <button
                          onClick={() => handleDelete(item)}
                          className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-medium text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                        >
                          <Trash2 className="h-3 w-3" />
                          {t('knownIssues.delete')}
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
          <PaginationBar
            page={page}
            totalPages={totalPages}
            totalItems={items.length}
            size={size}
            onPageChange={setPage}
            onSizeChange={(s) => { setSize(s); setPage(0); }}
          />
        </div>
      )}

      {/* Add / Edit modal */}
      {isModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
          style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }}
          onClick={closeModal}
        >
          <div
            className="w-full max-w-md sm:max-w-lg rounded-xl border animate-slide-up max-h-[90vh] flex flex-col"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-4 border-b flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {editing ? t('knownIssues.modalEditTitle') : t('knownIssues.modalNewTitle')}
              </h3>
              <button
                onClick={closeModal}
                className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
                style={{ color: 'var(--text-tertiary)' }}
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 flex flex-col min-h-0">
              <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('knownIssues.labelTitle')} *
                  </label>
                  <input
                    type="text"
                    value={form.title}
                    onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
                    maxLength={255}
                    placeholder={t('knownIssues.placeholderTitle')}
                    className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  />
                </div>

                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('knownIssues.labelContent')} *
                  </label>
                  <textarea
                    value={form.content}
                    onChange={(e) => setForm((prev) => ({ ...prev, content: e.target.value }))}
                    maxLength={10000}
                    rows={6}
                    placeholder={t('knownIssues.placeholderContent')}
                    className="w-full resize-y rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  />
                </div>

                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('knownIssues.labelTopic')}
                  </label>
                  <select
                    value={form.topicId}
                    onChange={(e) => setForm((prev) => ({ ...prev, topicId: e.target.value }))}
                    className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  >
                    <option value="">{t('knownIssues.noTopic')}</option>
                    {sortByLocalizedName(topics).map((tp) => (
                      <option key={tp.id} value={tp.id}>{localizedName(tp)}</option>
                    ))}
                  </select>
                </div>

                <label className="flex items-center gap-2.5 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.isActive}
                    onChange={(e) => setForm((prev) => ({ ...prev, isActive: e.target.checked }))}
                    className="h-4 w-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500 cursor-pointer"
                  />
                  <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {t('knownIssues.labelActive')}
                  </span>
                </label>
              </div>

              <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-5 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
                <button
                  type="button"
                  onClick={closeModal}
                  className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                >
                  {t('knownIssues.cancel')}
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                >
                  {saving ? t('knownIssues.saving') : t('knownIssues.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
