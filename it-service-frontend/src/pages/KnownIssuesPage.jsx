import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { LifeBuoy, Plus, Pencil, Trash2, X, Tag, Package, ChevronDown } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../services/api';
import {
  createKnownIssue,
  updateKnownIssue,
  deleteKnownIssue,
} from '../services/api';
import PaginationBar from '../components/PaginationBar';
import ListLoadingOverlay from '../components/ListLoadingOverlay';
import BilingualField from '../components/BilingualField';
import Button from '../components/Button';
import { useUrlState } from '../hooks/useUrlState';
import { usePagedFetch } from '../hooks/usePagedFetch';
import { localizedName, sortByLocalizedName, pickLocalized } from '../utils/localizedName';

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
  const [topics, setTopics]                     = useState([]);

  // Ürün / konu filtresi + sayfalama URL'de tutulur (F5 / yer imi / link paylaşımı korur).
  // Filtreleme + sayfalama sunucu taraflıdır (her değişimde backend'den yeni sayfa çekilir).
  const { str, num, setParams, searchParams } = useUrlState();
  const productParam = str('product');
  const selectedProductId = productParam ? Number(productParam) : null;
  const topicFilter = str('topic');
  const page = num('page', 0);
  const size = num('size', 10);
  const setSelectedProductId = (v) => setParams({ product: v ? String(v) : '', topic: '' });
  const setTopicFilter = (v) => setParams({ topic: v });
  const setPage = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize = (v) => setParams({ size: v === 10 ? '' : v });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing]         = useState(null);
  const [form, setForm]               = useState({ titleTr: '', titleEn: '', contentTr: '', contentEn: '', topicId: '', isActive: true });
  const [activeLang, setActiveLang]   = useState('tr'); // modal'daki çift dilli sekme (başlık + içerik ortak)
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
        // URL'deki ürün id'si geçersizse (yok ya da listede değil — örn. yeniden
        // seed sonrası id kaymış, eski link/yer imi) ilk ürüne düş; topic'i de sıfırla.
        if (res.data.length > 0) {
          const current = searchParams.get('product');
          const valid = current && res.data.some((p) => String(p.id) === String(current));
          if (!valid) setParams({ product: String(res.data[0].id), topic: '' });
        }
      })
      .catch(() => toast.error(t('knownIssues.errorLoadProducts')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
  // Listeleme — sunucu taraflı sayfalama/filtreleme
  // ---------------------------------------------------------------
  const {
    items: paginated, totalPages, totalItems,
    loading, initialLoading, error: fetchError, refetch,
  } = usePagedFetch(
    selectedProductId ? `/products/${selectedProductId}/known-issues/paged` : null,
    { topicId: topicFilter || undefined, includeInactive: canManage, page, size },
  );

  // Aralık dışı sayfa (örn. eski ?page= linki) son geçerli sayfaya çekilir.
  useEffect(() => {
    if (page > 0 && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, totalPages]);

  // ---------------------------------------------------------------
  // Modal akışı
  // ---------------------------------------------------------------
  const openCreate = () => {
    setEditing(null);
    setForm({ titleTr: '', titleEn: '', contentTr: '', contentEn: '', topicId: '', isActive: true });
    setActiveLang(i18n.language?.startsWith('tr') ? 'tr' : 'en');
    setIsModalOpen(true);
  };

  const openEdit = (item) => {
    setEditing(item);
    setForm({
      titleTr: item.titleTr ?? '',
      titleEn: item.titleEn ?? '',
      contentTr: item.contentTr ?? '',
      contentEn: item.contentEn ?? '',
      topicId: item.topicId ? String(item.topicId) : '',
      isActive: item.isActive ?? true,
    });
    // Düzenlemede dolu olan dil sekmesini aç (yoksa diğeri).
    setActiveLang(item.titleTr ? 'tr' : (item.titleEn ? 'en' : 'tr'));
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditing(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    // Her alan için en az bir dil zorunlu; ikinci dil opsiyonel (boşsa okuma anında diğerine düşer).
    if ((!form.titleTr.trim() && !form.titleEn.trim()) ||
        (!form.contentTr.trim() && !form.contentEn.trim())) {
      toast.error(t('knownIssues.errorRequired'));
      return;
    }
    const payload = {
      titleTr: form.titleTr.trim() || null,
      titleEn: form.titleEn.trim() || null,
      contentTr: form.contentTr.trim() || null,
      contentEn: form.contentEn.trim() || null,
      topicId: form.topicId ? Number(form.topicId) : null,
      isActive: form.isActive,
    };
    try {
      setSaving(true);
      if (editing) {
        await updateKnownIssue(editing.id, payload);
      } else {
        await createKnownIssue(selectedProductId, payload);
      }
      toast.success(t('knownIssues.saveSuccess'));
      closeModal();
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || t('knownIssues.errorSave'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    if (!window.confirm(t('knownIssues.confirmDelete', { title: localizedName(item, 'title') }))) return;
    try {
      await deleteKnownIssue(item.id);
      toast.success(t('knownIssues.deleteSuccess'));
      // Sayfadaki son kayıt silindiyse önceki sayfaya düş, yoksa mevcut sayfayı tazele.
      if (paginated.length === 1 && page > 0) setPage(page - 1);
      else refetch();
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

      {fetchError && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {fetchError}
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
              onChange={(e) => setSelectedProductId(Number(e.target.value))}
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
          <Button onClick={openCreate} fullWidth className="sm:w-auto">
            <Plus className="h-4 w-4" />
            {t('knownIssues.add')}
          </Button>
        )}
      </div>

      {/* Card grid */}
      <ListLoadingOverlay initial={initialLoading} loading={loading}>
      {totalItems === 0 ? (
        <div
          className="rounded-xl border py-12 text-center text-sm"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
        >
          {selectedProductId ? t('knownIssues.empty') : t('knownIssues.noProducts')}
        </div>
      ) : (
        <>
          {/* Ayrık genişleyen kartlar — her kayıt kendi kartı; sol durum şeridi +
              baştan ikon rozeti, içerik kart içinde yumuşakça açılır. */}
          <div className="space-y-3">
            {paginated.map((item) => {
              const isOpen = expandedIds.has(item.id);
              // Durum vurgusu: aktif kayıt primary, pasif kayıt nötr gri (tema-duyarlı).
              const accent = item.isActive ? 'var(--color-primary-500)' : 'var(--text-tertiary)';
              return (
                <div
                  key={item.id}
                  className="rounded-xl border overflow-hidden transition-shadow duration-200"
                  style={{
                    backgroundColor: 'var(--bg-surface)',
                    borderColor: 'var(--border-color)',
                    borderLeft: `3px solid ${accent}`,
                    boxShadow: isOpen ? 'var(--shadow-md)' : 'var(--shadow-sm)',
                  }}
                >
                  {/* Header — tiklanabilir, kayit icerigini acar/kapar */}
                  <button
                    type="button"
                    onClick={() => toggleExpanded(item.id)}
                    className="w-full flex items-center gap-3 px-4 sm:px-5 py-3.5 text-left transition-colors cursor-pointer hover:bg-[color:var(--bg-surface-hover)]"
                    aria-expanded={isOpen}
                  >
                    {/* Bastaki ikon rozeti — aktif/pasif duruma gore renklenir */}
                    <span
                      className="shrink-0 flex h-9 w-9 items-center justify-center rounded-lg"
                      style={
                        item.isActive
                          ? { backgroundColor: 'rgba(59, 130, 246, 0.12)', color: 'var(--color-primary-500)' }
                          : { backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-tertiary)' }
                      }
                    >
                      <LifeBuoy className="h-5 w-5" />
                    </span>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="text-sm font-semibold break-words min-w-0" style={{ color: 'var(--text-primary)' }}>
                          {localizedName(item, 'title')}
                        </h3>
                        {!item.isActive && (
                          <span className="shrink-0 inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300">
                            {t('knownIssues.statusInactive')}
                          </span>
                        )}
                      </div>
                      {item.topicId && topicLookup[item.topicId] && (
                        <span
                          className="mt-1 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                          style={{ backgroundColor: 'var(--bg-surface-secondary)', color: 'var(--text-secondary)' }}
                        >
                          <Tag className="h-3 w-3" />
                          {topicLookup[item.topicId]}
                        </span>
                      )}
                    </div>

                    <ChevronDown
                      className="h-4 w-4 shrink-0 transition-transform duration-200"
                      style={{
                        color: 'var(--text-tertiary)',
                        transform: isOpen ? 'rotate(0deg)' : 'rotate(-90deg)',
                      }}
                    />
                  </button>

                  {/* Body — sadece acikken render edilir; gomulu panelde icerik */}
                  {isOpen && (
                    <div className="px-4 sm:px-5 pb-4 animate-slide-up">
                      <div
                        className="rounded-lg border p-3.5"
                        style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}
                      >
                        <p
                          className="text-sm whitespace-pre-wrap leading-relaxed break-words"
                          style={{ color: 'var(--text-secondary)' }}
                        >
                          {pickLocalized(item.contentTr, item.contentEn)}
                        </p>
                      </div>
                      {canManage && (
                        <div className="mt-3 flex flex-wrap justify-end gap-2">
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
          </div>

          <PaginationBar
            page={page}
            totalPages={totalPages}
            totalItems={totalItems}
            size={size}
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        </>
      )}
      </ListLoadingOverlay>

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
                {/* Başlık + İçerik — iki dilli (tek TR/EN sekmesiyle, en az biri zorunlu) */}
                <BilingualField
                  label={t('knownIssues.labelTitle')}
                  required
                  lang={activeLang}
                  onLang={setActiveLang}
                  valueTr={form.titleTr}
                  valueEn={form.titleEn}
                  onChangeTr={(v) => setForm((prev) => ({ ...prev, titleTr: v }))}
                  onChangeEn={(v) => setForm((prev) => ({ ...prev, titleEn: v }))}
                  maxLength={255}
                  placeholder={t('knownIssues.placeholderTitle')}
                />

                <BilingualField
                  label={t('knownIssues.labelContent')}
                  required
                  hint={t('knownIssues.langHint')}
                  lang={activeLang}
                  onLang={setActiveLang}
                  showToggle={false}
                  as="textarea"
                  rows={5}
                  valueTr={form.contentTr}
                  valueEn={form.contentEn}
                  onChangeTr={(v) => setForm((prev) => ({ ...prev, contentTr: v }))}
                  onChangeEn={(v) => setForm((prev) => ({ ...prev, contentEn: v }))}
                  maxLength={10000}
                  placeholder={t('knownIssues.placeholderContent')}
                />

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
