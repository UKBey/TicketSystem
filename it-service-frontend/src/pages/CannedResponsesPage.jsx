import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Zap, Plus, Pencil, Trash2, X, Search, Star } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api, {
  createCannedResponse,
  updateCannedResponse,
  deleteCannedResponse,
  favoriteCannedResponse,
  unfavoriteCannedResponse,
} from '../services/api';
import { PLACEHOLDER_TOKENS, fillPlaceholders, availableLangs, pickContent } from '../utils/cannedResponses';
import { localizedName, sortByLocalizedName } from '../utils/localizedName';
import { formatDate } from '../utils/dateFormat';
import PaginationBar from '../components/PaginationBar';
import ListLoadingOverlay from '../components/ListLoadingOverlay';
import { useUrlState } from '../hooks/useUrlState';
import { usePagedFetch } from '../hooks/usePagedFetch';

const EMPTY_FORM = {
  title: '',
  shortcut: '',
  scope: 'PERSONAL',
  productId: '',
  visibility: 'BOTH',
  contentTr: '',
  contentEn: '',
};

/**
 * Hazır Yanıtlar yönetim sayfası (Ayarlar > Hazır Yanıtlar).
 *
 * - Agent yalnız kendi kişisel şablonlarını oluşturur/düzenler/siler.
 * - LEAD_AGENT / ADMIN ayrıca paylaşılan (ekip/ürün) şablonları yönetir.
 * - Müşteri bu sayfaya hiç erişemez (route ProtectedRoute ile korunur).
 */
export default function CannedResponsesPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const { isLeadAgent, isAdmin, user } = useAuth();
  // Paylaşılan (SHARED) şablonları yönetme yetkisi — lead agent veya admin.
  const canManageShared = isLeadAgent || isAdmin;

  const [products, setProducts] = useState([]);
  // Favori değişimleri yerel olarak override edilir (her yıldız tıklamasında refetch/dim olmasın).
  const [favOverrides, setFavOverrides] = useState({});
  // Arama + filtreler + sayfalama URL'de tutulur (F5 / yer imi / link paylaşımı korur).
  // Filtreleme + sayfalama sunucu taraflıdır (her değişimde backend'den yeni sayfa çekilir).
  const { str, num, setParams } = useUrlState();
  const search           = str('search');
  const scopeFilter      = str('scope', 'ALL');
  const productFilter    = str('product', 'ALL');
  const langFilter       = str('lang', 'ALL');
  const visibilityFilter = str('visibility', 'ALL');
  const page = num('page', 0);
  const size = num('size', 20);
  const setSearch           = (v) => setParams({ search: v });
  const setScopeFilter      = (v) => setParams({ scope: v === 'ALL' ? '' : v });
  const setProductFilter    = (v) => setParams({ product: v === 'ALL' ? '' : v });
  const setLangFilter       = (v) => setParams({ lang: v === 'ALL' ? '' : v });
  const setVisibilityFilter = (v) => setParams({ visibility: v === 'ALL' ? '' : v });
  const setPage = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize = (v) => setParams({ size: v === 20 ? '' : v });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [activeLang, setActiveLang] = useState('tr');
  const [saving, setSaving] = useState(false);
  const contentRef = useRef(null);

  const productLookup = useMemo(() => {
    const map = {};
    products.forEach((p) => { map[p.id] = localizedName(p); });
    return map;
    // localizedName aktif dili global i18n'den okur — dil değişince yeniden hesaplanmalı.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [products, i18n.language]);

  const sampleCtx = useMemo(() => {
    const tr = i18n.language?.startsWith('tr');
    return {
      'musteri.ad': tr ? 'Ahmet Yılmaz' : 'John Doe',
      'agent.ad': user?.name || 'Agent',
      'bilet.no': 'TCK-001',
      urun: 'VPN',
      konu: tr ? 'Bağlantı sorunu' : 'Connection issue',
      tarih: formatDate(new Date()),
    };
  }, [i18n.language, user]);

  // ---- data ----------------------------------------------------------------
  useEffect(() => {
    api.get('/products').then((res) => setProducts(res.data || [])).catch(() => setProducts([]));
  }, []);

  // Sunucu taraflı filtreleme + sayfalama. product filtresi 3 modlu: ALL / GLOBAL / belirli ürün.
  const {
    items: pageItems, totalItems, totalPages,
    loading, initialLoading, error: fetchError, refetch,
  } = usePagedFetch('/canned-responses/paged', {
    q: search.trim() || undefined,
    scope: scopeFilter === 'ALL' ? undefined : scopeFilter,
    visibility: visibilityFilter === 'ALL' ? undefined : visibilityFilter,
    lang: langFilter === 'ALL' ? undefined : langFilter,
    global: productFilter === 'GLOBAL' ? true : undefined,
    productId: (productFilter !== 'ALL' && productFilter !== 'GLOBAL') ? productFilter : undefined,
    page, size,
  });

  // Aralık dışı sayfa (örn. eski ?page= linki) son geçerli sayfaya çekilir.
  useEffect(() => {
    if (page > 0 && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, totalPages]);

  const canManageItem = (item) =>
    (item.scope === 'PERSONAL' && item.ownerAgentId === user?.id)
    || (item.scope === 'SHARED' && canManageShared);

  const isFavorite = (item) => (item.id in favOverrides ? favOverrides[item.id] : item.favorite);

  // ---- modal ---------------------------------------------------------------
  const openCreate = () => {
    setEditing(null);
    setForm({ ...EMPTY_FORM, scope: canManageShared ? 'PERSONAL' : 'PERSONAL' });
    setActiveLang(i18n.language?.startsWith('tr') ? 'tr' : 'en');
    setIsModalOpen(true);
  };

  const openEdit = (item) => {
    setEditing(item);
    setForm({
      title: item.title ?? '',
      shortcut: item.shortcut ?? '',
      scope: item.scope ?? 'PERSONAL',
      productId: item.productId ? String(item.productId) : '',
      visibility: item.visibility ?? 'BOTH',
      contentTr: item.contentTr ?? '',
      contentEn: item.contentEn ?? '',
    });
    setActiveLang(item.contentTr ? 'tr' : (item.contentEn ? 'en' : 'tr'));
    setIsModalOpen(true);
  };

  const closeModal = () => { setIsModalOpen(false); setEditing(null); };

  const insertPlaceholder = (token) => {
    const field = activeLang === 'tr' ? 'contentTr' : 'contentEn';
    const el = contentRef.current;
    const current = form[field] || '';
    // When the textarea isn't focused, some engines report selection 0/0; append instead of prepending.
    const focused = el && document.activeElement === el;
    const start = focused ? el.selectionStart : current.length;
    const end = focused ? el.selectionEnd : current.length;
    const snippet = `{{${token}}}`;
    const next = current.slice(0, start) + snippet + current.slice(end);
    setForm((prev) => ({ ...prev, [field]: next }));
    requestAnimationFrame(() => {
      if (el) { el.focus(); const pos = start + snippet.length; el.setSelectionRange(pos, pos); }
    });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const title = form.title.trim();
    const tr = form.contentTr.trim();
    const en = form.contentEn.trim();
    if (!title || (!tr && !en)) {
      toast.error(t('cannedResponses.errorRequired'));
      return;
    }
    const scope = canManageShared ? form.scope : 'PERSONAL';
    const payload = {
      title,
      shortcut: form.shortcut.trim() || null,
      scope,
      // Either scope may be product-scoped (or global when no product chosen).
      productId: form.productId ? Number(form.productId) : null,
      visibility: form.visibility,
      contentTr: tr || null,
      contentEn: en || null,
    };
    try {
      setSaving(true);
      if (editing) {
        await updateCannedResponse(editing.id, payload);
      } else {
        await createCannedResponse(payload);
      }
      closeModal();
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || t('cannedResponses.errorSave'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    if (!window.confirm(t('cannedResponses.confirmDelete', { title: item.title }))) return;
    try {
      await deleteCannedResponse(item.id);
      // Sayfadaki son kayıt silindiyse önceki sayfaya düş, yoksa mevcut sayfayı tazele.
      if (pageItems.length === 1 && page > 0) setPage(page - 1);
      else refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || t('cannedResponses.errorDelete'));
    }
  };

  // Favorilere ekle/çıkar — iyimser yerel override (refetch/dim olmadan), hatada geri al.
  // Favori kişiseldir: kullanıcı yönetemediği (paylaşılan) şablonları da favoriye alabilir.
  const handleToggleFavorite = async (item) => {
    const next = !isFavorite(item);
    setFavOverrides((prev) => ({ ...prev, [item.id]: next }));
    try {
      if (next) await favoriteCannedResponse(item.id);
      else await unfavoriteCannedResponse(item.id);
    } catch (err) {
      setFavOverrides((prev) => ({ ...prev, [item.id]: !next }));
      toast.error(err.response?.data?.message || t('cannedResponses.errorFavorite'));
    }
  };

  const contentField = activeLang === 'tr' ? 'contentTr' : 'contentEn';
  const previewText = fillPlaceholders(form[contentField] || '', sampleCtx);

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
          <Zap className="h-6 w-6 text-primary-500" />
          {t('cannedResponses.manageTitle')}
        </h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
          {t('cannedResponses.manageSubtitle')}
        </p>
      </div>

      {fetchError && (
        <div className="rounded-lg px-4 py-3 mb-4 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {fetchError}
        </div>
      )}

      {/* Toolbar */}
      <div
        className="rounded-xl border p-4 mb-5 flex flex-col gap-2 sm:gap-3 sm:flex-row sm:items-end sm:justify-between"
        style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
      >
        <div className="flex flex-col gap-2 sm:gap-3 sm:flex-row sm:items-end flex-1 min-w-0">
          <div className="w-full sm:flex-1 sm:min-w-[14rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
              {t('cannedResponses.searchPlaceholder')}
            </label>
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2" style={{ color: 'var(--text-tertiary)' }} />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('cannedResponses.searchPlaceholder')}
                className="w-full rounded-lg border py-2 pl-8 pr-2 text-sm outline-none focus:ring-2"
                style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
              />
            </div>
          </div>
          <div className="w-full sm:w-auto sm:min-w-[10rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
              {t('cannedResponses.filterScope')}
            </label>
            <select
              value={scopeFilter}
              onChange={(e) => setScopeFilter(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              <option value="ALL">{t('cannedResponses.scopeAll')}</option>
              <option value="PERSONAL">{t('cannedResponses.scopePersonal')}</option>
              <option value="SHARED">{t('cannedResponses.scopeTeam')}</option>
            </select>
          </div>
          <div className="w-full sm:w-auto sm:min-w-[10rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
              {t('cannedResponses.filterProduct')}
            </label>
            <select
              value={productFilter}
              onChange={(e) => setProductFilter(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              <option value="ALL">{t('cannedResponses.allProducts')}</option>
              <option value="GLOBAL">{t('cannedResponses.productGlobal')}</option>
              {sortByLocalizedName(products).map((p) => <option key={p.id} value={p.id}>{localizedName(p)}</option>)}
            </select>
          </div>
          <div className="w-full sm:w-auto sm:min-w-[7rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
              {t('cannedResponses.filterLang')}
            </label>
            <select
              value={langFilter}
              onChange={(e) => setLangFilter(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              <option value="ALL">{t('cannedResponses.filterAll')}</option>
              <option value="tr">TR</option>
              <option value="en">EN</option>
            </select>
          </div>
          <div className="w-full sm:w-auto sm:min-w-[9rem] min-w-0">
            <label className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-tertiary)' }}>
              {t('cannedResponses.filterVisibility')}
            </label>
            <select
              value={visibilityFilter}
              onChange={(e) => setVisibilityFilter(e.target.value)}
              className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer focus:ring-2"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
            >
              <option value="ALL">{t('cannedResponses.filterAll')}</option>
              <option value="EXTERNAL">{t('cannedResponses.visExternal')}</option>
              <option value="INTERNAL">{t('cannedResponses.visInternal')}</option>
              <option value="BOTH">{t('cannedResponses.visBoth')}</option>
            </select>
          </div>
        </div>

        <button
          onClick={openCreate}
          className="inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer w-full sm:w-auto"
        >
          <Plus className="h-4 w-4" />
          {t('cannedResponses.add')}
        </button>
      </div>

      {/* List */}
      <ListLoadingOverlay initial={initialLoading} loading={loading}>
      {totalItems === 0 ? (
        <div
          className="rounded-xl border py-12 text-center text-sm"
          style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
        >
          {search.trim() ? t('cannedResponses.noResults') : t('cannedResponses.emptyManage')}
        </div>
      ) : (
        <>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {pageItems.map((item) => {
            const langs = availableLangs(item);
            // Seçili UI dilinde göster; o dil yoksa pickContent diğer varyanta düşer.
            const { content } = pickContent(item, i18n.language?.startsWith('tr') ? 'tr' : 'en');
            const preview = fillPlaceholders(content, sampleCtx);
            return (
              <div
                key={item.id}
                className="rounded-xl border p-4 flex flex-col gap-2"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <h3 className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>{item.title}</h3>
                      {item.shortcut && (
                        <span className="text-[11px] font-mono" style={{ color: 'var(--text-tertiary)' }}>/{item.shortcut}</span>
                      )}
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 flex-wrap">
                      <Badge tone={item.scope === 'SHARED' ? 'blue' : 'slate'}>
                        {item.scope === 'SHARED' ? t('cannedResponses.badgeShared') : t('cannedResponses.badgePersonal')}
                      </Badge>
                      {item.scope === 'SHARED' && item.productId && (
                        <Badge tone="slate">{productLookup[item.productId] || `#${item.productId}`}</Badge>
                      )}
                      <Badge tone={item.visibility === 'INTERNAL' ? 'amber' : item.visibility === 'EXTERNAL' ? 'green' : 'slate'}>
                        {item.visibility === 'INTERNAL' ? t('cannedResponses.visInternal')
                          : item.visibility === 'EXTERNAL' ? t('cannedResponses.visExternal')
                            : t('cannedResponses.visBoth')}
                      </Badge>
                      {langs.map((l) => <Badge key={l} tone="slate">{l.toUpperCase()}</Badge>)}
                    </div>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button
                      onClick={() => handleToggleFavorite(item)}
                      className="flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer"
                      style={{ borderColor: 'var(--border-color)' }}
                      title={t('cannedResponses.toggleFavorite')}
                      aria-label={t('cannedResponses.toggleFavorite')}
                      aria-pressed={isFavorite(item)}
                    >
                      <Star
                        className="h-3.5 w-3.5"
                        fill={isFavorite(item) ? '#f59e0b' : 'none'}
                        style={{ color: isFavorite(item) ? '#f59e0b' : 'var(--text-tertiary)' }}
                      />
                    </button>
                    {canManageItem(item) && (
                      <>
                        <button
                          onClick={() => openEdit(item)}
                          className="flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                          title={t('cannedResponses.edit')}
                          aria-label={t('cannedResponses.edit')}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </button>
                        <button
                          onClick={() => handleDelete(item)}
                          className="flex h-7 w-7 items-center justify-center rounded-lg text-white bg-danger-500 hover:bg-danger-600 transition-colors cursor-pointer"
                          title={t('cannedResponses.delete')}
                          aria-label={t('cannedResponses.delete')}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </>
                    )}
                  </div>
                </div>
                <p className="text-xs whitespace-pre-wrap break-words line-clamp-3" style={{ color: 'var(--text-secondary)' }}>
                  {preview}
                </p>
              </div>
            );
          })}
        </div>

        <div className="mt-3">
          <PaginationBar
            page={page}
            totalPages={totalPages}
            totalItems={totalItems}
            size={size}
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        </div>
        </>
      )}
      </ListLoadingOverlay>

      {/* Create / Edit modal */}
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
                {editing ? t('cannedResponses.modalEditTitle') : t('cannedResponses.modalNewTitle')}
              </h3>
              <button
                onClick={closeModal}
                className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500"
                style={{ color: 'var(--text-tertiary)' }}
                aria-label={t('cannedResponses.cancel')}
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSave} className="flex-1 flex flex-col min-h-0">
              <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
                {/* Title */}
                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('cannedResponses.labelTitle')} *
                  </label>
                  <input
                    type="text"
                    value={form.title}
                    onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                    maxLength={150}
                    placeholder={t('cannedResponses.placeholderTitle')}
                    className="w-full rounded-lg border px-3 py-2 text-sm outline-none focus:ring-2"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  />
                </div>

                {/* Shortcut */}
                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('cannedResponses.labelShortcut')}
                  </label>
                  <input
                    type="text"
                    value={form.shortcut}
                    onChange={(e) => setForm((p) => ({ ...p, shortcut: e.target.value }))}
                    maxLength={50}
                    placeholder={t('cannedResponses.placeholderShortcut')}
                    className="w-full rounded-lg border px-3 py-2 text-sm outline-none focus:ring-2"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  />
                  <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('cannedResponses.shortcutHint')}</p>
                </div>

                {/* Scope + Product */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                      {t('cannedResponses.labelScope')}
                    </label>
                    <select
                      value={form.scope}
                      onChange={(e) => setForm((p) => ({ ...p, scope: e.target.value }))}
                      disabled={!canManageShared}
                      className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer disabled:opacity-60"
                      style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                    >
                      <option value="PERSONAL">{t('cannedResponses.scopePersonalOption')}</option>
                      <option value="SHARED">{t('cannedResponses.scopeSharedOption')}</option>
                    </select>
                  </div>
                  {/* Product binding applies to both scopes (personal or shared). */}
                  <div>
                      <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                        {t('cannedResponses.labelProduct')}
                      </label>
                      <select
                        value={form.productId}
                        onChange={(e) => setForm((p) => ({ ...p, productId: e.target.value }))}
                        className="w-full rounded-lg border px-3 py-2 text-sm outline-none cursor-pointer"
                        style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                      >
                        <option value="">{t('cannedResponses.productGlobal')}</option>
                        {sortByLocalizedName(products).map((p) => <option key={p.id} value={p.id}>{localizedName(p)}</option>)}
                      </select>
                  </div>
                </div>

                {/* Visibility */}
                <div>
                  <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
                    {t('cannedResponses.labelVisibility')}
                  </label>
                  <div className="flex flex-wrap gap-2">
                    {['EXTERNAL', 'INTERNAL', 'BOTH'].map((v) => (
                      <button
                        key={v}
                        type="button"
                        onClick={() => setForm((p) => ({ ...p, visibility: v }))}
                        className="rounded-full px-3 py-1.5 text-xs font-semibold border transition-colors cursor-pointer"
                        style={form.visibility === v
                          ? { backgroundColor: 'var(--color-primary-500, #3b82f6)', color: '#fff', borderColor: 'transparent' }
                          : { borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                      >
                        {v === 'EXTERNAL' ? t('cannedResponses.visExternal') : v === 'INTERNAL' ? t('cannedResponses.visInternal') : t('cannedResponses.visBoth')}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Content tabs (TR / EN) */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {t('cannedResponses.labelContent')} *
                    </label>
                    <div className="flex overflow-hidden rounded-md border" style={{ borderColor: 'var(--border-color)' }}>
                      {['tr', 'en'].map((l) => (
                        <button
                          key={l}
                          type="button"
                          onClick={() => setActiveLang(l)}
                          className="px-2.5 py-1 text-xs font-bold uppercase transition-colors cursor-pointer"
                          style={activeLang === l
                            ? { backgroundColor: 'var(--color-primary-500, #3b82f6)', color: '#fff' }
                            : { color: 'var(--text-tertiary)', backgroundColor: 'transparent' }}
                        >
                          {l}
                        </button>
                      ))}
                    </div>
                  </div>
                  <textarea
                    ref={contentRef}
                    value={form[contentField]}
                    onChange={(e) => setForm((p) => ({ ...p, [contentField]: e.target.value }))}
                    maxLength={2000}
                    rows={5}
                    placeholder={t('cannedResponses.placeholderContent')}
                    className="w-full resize-y rounded-lg border px-3 py-2 text-sm outline-none focus:ring-2"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
                  />
                  {/* Placeholder helper */}
                  <div className="mt-2">
                    <p className="text-xs mb-1" style={{ color: 'var(--text-tertiary)' }}>{t('cannedResponses.placeholdersHelp')}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {PLACEHOLDER_TOKENS.map((token) => (
                        <button
                          key={token}
                          type="button"
                          onClick={() => insertPlaceholder(token)}
                          className="rounded-md border px-2 py-0.5 text-[11px] font-mono transition-colors cursor-pointer hover:border-primary-500"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                        >
                          {`{{${token}}}`}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Live preview */}
                <div>
                  <label className="block text-xs font-semibold mb-1" style={{ color: 'var(--text-tertiary)' }}>
                    {t('cannedResponses.preview')}
                  </label>
                  <div
                    className="rounded-lg border px-3 py-2 text-sm whitespace-pre-wrap break-words min-h-[2.5rem]"
                    style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                  >
                    {previewText || <span style={{ color: 'var(--text-tertiary)' }}>{t('cannedResponses.noContentLang')}</span>}
                  </div>
                </div>
              </div>

              <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3 px-5 py-4 border-t flex-shrink-0" style={{ borderColor: 'var(--border-color)' }}>
                <button
                  type="button"
                  onClick={closeModal}
                  className="rounded-lg border px-4 py-2 text-sm font-semibold transition-colors cursor-pointer"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)', backgroundColor: 'transparent' }}
                >
                  {t('cannedResponses.cancel')}
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors disabled:opacity-50 cursor-pointer"
                >
                  {saving ? t('cannedResponses.saving') : t('cannedResponses.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

function Badge({ tone = 'slate', children }) {
  const tones = {
    slate: { backgroundColor: 'rgba(148,163,184,0.18)', color: 'var(--text-secondary)' },
    blue: { backgroundColor: 'rgba(59,130,246,0.15)', color: '#3b82f6' },
    amber: { backgroundColor: 'rgba(245,158,11,0.18)', color: '#b45309' },
    green: { backgroundColor: 'rgba(16,185,129,0.15)', color: '#059669' },
  };
  return (
    <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide" style={tones[tone]}>
      {children}
    </span>
  );
}
