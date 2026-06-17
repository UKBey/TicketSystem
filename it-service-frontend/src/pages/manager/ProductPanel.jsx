import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Trash2, X, Eye, Search, Tag, ChevronDown, ChevronUp, BarChart3 } from 'lucide-react';
import api from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import PaginationBar from '../../components/PaginationBar';
import ProductTopicsSection from '../../components/ProductTopicsSection';
import SortableTh from '../../components/SortableTh';
import ListLoadingOverlay from '../../components/ListLoadingOverlay';
import BilingualField from '../../components/BilingualField';
import { useColumnResize } from '../../hooks/useColumnResize';
import { useUrlState } from '../../hooks/useUrlState';
import { usePagedFetch } from '../../hooks/usePagedFetch';
import { localizedName } from '../../utils/localizedName';
import Button from '../../components/Button';

const PAGE_SIZE = 10;

// Sürüklenebilir sütun varsayılan genişlikleri (px). Son sütun (actions) esner.
const COL_WIDTHS = { id: 90, name: 280, status: 130, maxActiveTickets: 350, actions: 170 };
const COL_ORDER = ['id', 'name', 'status', 'maxActiveTickets', 'actions'];

export default function ProductPanel() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const navigate = useNavigate();
  const { tableWidth, handleFor, renderColgroup } = useColumnResize(COL_WIDTHS, COL_ORDER, 'colw:products');
  const { isLeadAgent, isAdmin, isManager } = useAuth();
  // Ürün CRUD (oluştur/düzenle/sil) sistem-config'tir — yalnızca admin.
  const canManageProducts = isAdmin;
  // Konu (topic) yönetimi ürün içeriğidir — lead agent veya admin.
  const canManageTopics = isLeadAgent || isAdmin;
  // Ürün dashboard'u oversight'tir — admin/manager/lead (lead yalnızca yetkili ürünleri).
  const canViewDashboard = isAdmin || isManager || isLeadAgent;

  // Filtre / sayfa / sıralama state'i URL'de tutulur (F5 / yer imi / link paylaşımı korur).
  const { str, num, setParams } = useUrlState();
  const search  = str('search');
  const page    = num('page', 0);
  const size    = num('size', PAGE_SIZE);
  const sortBy  = str('sortBy', 'id');
  const sortDir = str('sortDir', 'asc');
  const setSearch = (v) => setParams({ search: v });
  const setPage   = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize   = (v) => setParams({ size: v === PAGE_SIZE ? '' : v });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentProduct, setCurrentProduct] = useState(null);
  const [formData, setFormData] = useState({ nameTr: '', nameEn: '', isActive: true, maxActiveTickets: '' });
  const [activeLang, setActiveLang] = useState('tr'); // modal'daki çift dilli ad sekmesi
  const [expandedTopicsProductId, setExpandedTopicsProductId] = useState(null);

  // Sunucu taraflı filtreleme + sıralama + sayfalama. Lokalize ad sıralaması için aktif dil
  // backend'e iletilir (name → nameTr/nameEn). Ürün kataloğu küçük; backend tek sayfa döner.
  const {
    items: paginated, totalItems, totalPages,
    loading, initialLoading, error: fetchError, refetch,
  } = usePagedFetch('/products/paged', {
    search: search || undefined,
    sortBy, sortDir,
    lang: i18n.language?.startsWith('tr') ? 'tr' : 'en',
    page, size,
  });

  // Aralık dışı sayfa (örn. eski ?page= linki) son geçerli sayfaya çekilir.
  useEffect(() => {
    if (page > 0 && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, totalPages]);

  const toggleSort = (field) => {
    const nextDir = sortBy === field ? (sortDir === 'asc' ? 'desc' : 'asc') : 'asc';
    setParams({ sortBy: field === 'id' ? '' : field, sortDir: nextDir === 'asc' ? '' : nextDir });
  };

  const openModal = (product = null) => {
    if (product) {
      setCurrentProduct(product);
      setFormData({
        nameTr: product.nameTr ?? '',
        nameEn: product.nameEn ?? '',
        isActive: product.isActive,
        maxActiveTickets: product.maxActiveTickets ?? ''
      });
      // Düzenlemede dolu olan dil sekmesini aç (yoksa diğeri).
      setActiveLang(product.nameTr ? 'tr' : (product.nameEn ? 'en' : 'tr'));
    } else {
      setCurrentProduct(null);
      setFormData({ nameTr: '', nameEn: '', isActive: true, maxActiveTickets: '' });
      setActiveLang(i18n.language?.startsWith('tr') ? 'tr' : 'en');
    }
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setCurrentProduct(null);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!formData.nameTr.trim() && !formData.nameEn.trim()) {
      toast.error(t('productPanel.errorNameRequired'));
      return;
    }

    const limit = formData.maxActiveTickets === '' ? null : Number(formData.maxActiveTickets);
    if (limit !== null && (Number.isNaN(limit) || limit < 1 || limit > 10000)) {
      toast.error(t('productPanel.errorLimitRange'));
      return;
    }

    const payload = {
      ...formData,
      maxActiveTickets: limit
    };

    try {
      if (currentProduct) {
        await api.put(`/products/${currentProduct.id}`, payload);
      } else {
        await api.post('/products', payload);
      }
      toast.success(t('productPanel.saveSuccess'));
      closeModal();
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || t('productPanel.errorSave'));
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('productPanel.confirmDelete'))) return;
    try {
      await api.delete(`/products/${id}`);
      toast.success(t('productPanel.deleteSuccess'));
      // Sayfadaki son kayıt silindiyse önceki sayfaya düş, yoksa mevcut sayfayı tazele.
      if (paginated.length === 1 && page > 0) setPage(page - 1);
      else refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || t('productPanel.errorDelete'));
    }
  };

  return (
    <>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('productPanel.title')}</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {canManageProducts ? t('productPanel.subtitleAdmin') : t('productPanel.subtitleUser')}
          </p>
        </div>
        {canManageProducts && (
          <Button
            onClick={() => openModal()}
            data-tour="products-create"
            fullWidth
            className="sm:w-auto hover:shadow-lg hover:shadow-primary-500/25"
          >
            <Plus className="h-4 w-4" />
            {t('productPanel.newProduct')}
          </Button>
        )}
      </div>

      {fetchError && (
        <div className="rounded-lg px-4 py-3 mb-5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {fetchError}
        </div>
      )}

      <div data-tour="products-table" className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>
        {/* Header + search */}
        <div className="px-4 py-4 border-b flex flex-col sm:flex-row sm:flex-wrap sm:items-center sm:justify-between gap-3 sm:px-6"
          style={{ borderColor: 'var(--border-color)' }}>
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('productPanel.systemProducts')}
            <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
              {t('productPanel.totalCount', { count: totalItems })}
            </span>
          </span>
          <div className="relative w-full sm:w-auto">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 pointer-events-none"
              style={{ color: 'var(--text-tertiary)' }} />
            <input
              type="text"
              placeholder={t('productPanel.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="rounded-lg border pl-8 pr-8 py-1.5 text-xs outline-none focus:ring-2 w-full sm:w-48"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            />
            {search && (
              <button onClick={() => setSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer"
                style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>

        <ListLoadingOverlay initial={initialLoading} loading={loading}>
        <ul className="lg:hidden space-y-3 p-4">
          {paginated.map(product => {
            const topicsOpen = expandedTopicsProductId === product.id;
            return (
              <li
                key={product.id}
                className="rounded-xl border p-4"
                style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}
              >
                <div className="flex items-start justify-between gap-2 mb-2">
                  <span className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>
                    {localizedName(product)}
                  </span>
                  <span className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-[10px] font-semibold ${
                    product.isActive
                      ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                      : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                  }`}>
                    {product.isActive ? t('productPanel.statusActive') : t('productPanel.statusInactive')}
                  </span>
                </div>
                <dl className="text-xs space-y-1" style={{ color: 'var(--text-secondary)' }}>
                  <div className="flex justify-between gap-2">
                    <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                      {t('productPanel.colId')}
                    </dt>
                    <dd className="text-right font-mono">{product.id}</dd>
                  </div>
                  <div className="flex justify-between gap-2">
                    <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                      {t('productPanel.colMaxTickets')}
                    </dt>
                    <dd className="text-right">
                      {product.maxActiveTickets == null ? (
                        <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300">
                          {t('productPanel.unlimited')}
                        </span>
                      ) : (
                        <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                          {product.maxActiveTickets}
                        </span>
                      )}
                    </dd>
                  </div>
                </dl>
                <div className="mt-3 pt-3 border-t flex flex-col gap-2" style={{ borderColor: 'var(--border-color-light)' }}>
                  <button
                    type="button"
                    onClick={() => setExpandedTopicsProductId(topicsOpen ? null : product.id)}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer"
                    style={{
                      borderColor: topicsOpen ? '#3b82f6' : 'var(--border-color)',
                      backgroundColor: topicsOpen ? 'rgba(59,130,246,0.12)' : 'transparent',
                      color: topicsOpen ? '#2563eb' : 'var(--text-secondary)',
                    }}
                  >
                    <Tag className="h-3.5 w-3.5" />
                    {t('productPanel.manageTopics')}
                    {topicsOpen ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => navigate(`/products/${product.id}`)}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
                    style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                  >
                    <Eye className="h-3.5 w-3.5" />
                    {t('productPanel.view')}
                  </button>
                  {canViewDashboard && (
                    <button
                      type="button"
                      onClick={() => navigate(`/products/${product.id}/dashboard`, { state: { product: { id: product.id, nameTr: product.nameTr, nameEn: product.nameEn } } })}
                      className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
                      style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                    >
                      <BarChart3 className="h-3.5 w-3.5" />
                      {t('productDashboard.viewAction')}
                    </button>
                  )}
                  {canManageProducts && (
                    <>
                      <button
                        type="button"
                        onClick={() => openModal(product)}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
                        style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                      >
                        <Pencil className="h-3.5 w-3.5" />
                        {t('productPanel.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(product.id)}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                        style={{ borderColor: 'var(--border-color)', color: '#ef4444' }}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        {t('productPanel.delete')}
                      </button>
                    </>
                  )}
                </div>
                {topicsOpen && (
                  <div className="mt-3 pt-3 border-t" style={{ borderColor: 'var(--border-color-light)' }}>
                    <ProductTopicsSection productId={product.id} isAdmin={canManageTopics} />
                  </div>
                )}
              </li>
            );
          })}
          {paginated.length === 0 && (
            <li
              className="rounded-xl border text-center py-12 text-sm"
              style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
            >
              {search ? t('productPanel.noProductsFiltered') : t('productPanel.noProducts')}
            </li>
          )}
        </ul>

        <div className="hidden lg:block overflow-x-auto">
          <table className="w-full resizable-table" style={{ tableLayout: 'fixed', minWidth: `${tableWidth}px` }}>
            {renderColgroup()}
            <thead>
              <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                <SortableTh field="id"               label={t('productPanel.colId')}         sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('id')} />
                <SortableTh field="name"             label={t('productPanel.colName')}       sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('name')} />
                <SortableTh field="status"           label={t('productPanel.colStatus')}     sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('status')} />
                <SortableTh field="maxActiveTickets" label={t('productPanel.colMaxTickets')} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('maxActiveTickets')} />
                <th className="text-right px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b" style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>{t('productPanel.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {paginated.map(product => {
                const topicsOpen = expandedTopicsProductId === product.id;
                return (
                <React.Fragment key={product.id}>
                <tr className={topicsOpen ? 'is-open' : undefined}>
                  <td className="px-4 py-3 text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>{product.id}</td>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{localizedName(product)}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      product.isActive
                        ? 'bg-accent-100 text-accent-700 dark:bg-accent-500/20 dark:text-accent-300'
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300'
                    }`}>
                      {product.isActive ? t('productPanel.statusActive') : t('productPanel.statusInactive')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                    {product.maxActiveTickets == null ? (
                      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300">
                        {t('productPanel.unlimited')}
                      </span>
                    ) : (
                      <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300">
                        {product.maxActiveTickets}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <button
                        type="button"
                        className="inline-flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer"
                        style={{
                          borderColor: topicsOpen ? '#3b82f6' : 'var(--border-color)',
                          backgroundColor: topicsOpen ? 'rgba(59,130,246,0.12)' : 'transparent',
                          color: topicsOpen ? '#2563eb' : 'var(--text-secondary)',
                        }}
                        onClick={() => setExpandedTopicsProductId(topicsOpen ? null : product.id)}
                        title={t('productPanel.manageTopics')}
                        aria-label={t('productPanel.manageTopics')}
                      >
                        <Tag className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        className="inline-flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
                        style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                        onClick={() => navigate(`/products/${product.id}`)}
                        title={t('productPanel.view')}
                        aria-label={t('productPanel.view')}
                      >
                        <Eye className="h-3.5 w-3.5" />
                      </button>
                      {canViewDashboard && (
                        <button
                          type="button"
                          className="inline-flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                          onClick={() => navigate(`/products/${product.id}/dashboard`, { state: { product: { id: product.id, nameTr: product.nameTr, nameEn: product.nameEn } } })}
                          title={t('productDashboard.viewAction')}
                          aria-label={t('productDashboard.viewAction')}
                        >
                          <BarChart3 className="h-3.5 w-3.5" />
                        </button>
                      )}
                      {canManageProducts && (
                        <>
                          <button
                            type="button"
                            className="inline-flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer hover:bg-[var(--bg-surface-hover)]"
                            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                            onClick={() => openModal(product)}
                            title={t('productPanel.edit')}
                            aria-label={t('productPanel.edit')}
                          >
                            <Pencil className="h-3.5 w-3.5" />
                          </button>
                          <button
                            type="button"
                            className="inline-flex h-7 w-7 items-center justify-center rounded-lg border transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                            style={{ borderColor: 'var(--border-color)', color: '#ef4444' }}
                            onClick={() => handleDelete(product.id)}
                            title={t('productPanel.delete')}
                            aria-label={t('productPanel.delete')}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
                {topicsOpen && (
                  <tr>
                    <td colSpan="5" className="px-6 pb-4 pt-0">
                      <ProductTopicsSection productId={product.id} isAdmin={canManageTopics} />
                    </td>
                  </tr>
                )}
                </React.Fragment>
                );
              })}
              {paginated.length === 0 && (
                <tr>
                  <td colSpan="5" className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                    {search ? t('productPanel.noProductsFiltered') : t('productPanel.noProducts')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <PaginationBar
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          size={size}
          onPageChange={setPage}
          onSizeChange={setSize}
        />
        </ListLoadingOverlay>
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in" style={{ backgroundColor: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} onClick={closeModal}>
          <div
            className="w-full max-w-md rounded-xl border animate-slide-up"
            style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-xl)' }}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b" style={{ borderColor: 'var(--border-color)' }}>
              <h3 className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                {currentProduct ? t('productPanel.modalEditTitle') : t('productPanel.modalNewTitle')}
              </h3>
              <button onClick={closeModal} className="flex h-8 w-8 items-center justify-center rounded-lg transition-colors cursor-pointer hover:bg-danger-50 hover:text-danger-500" style={{ color: 'var(--text-tertiary)' }}>
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4">
              <BilingualField
                label={t('productPanel.labelName')}
                required
                hint={t('productPanel.nameHint')}
                lang={activeLang}
                onLang={setActiveLang}
                valueTr={formData.nameTr}
                valueEn={formData.nameEn}
                onChangeTr={(v) => setFormData({ ...formData, nameTr: v })}
                onChangeEn={(v) => setFormData({ ...formData, nameEn: v })}
                placeholderTr="örn. E-Ticaret Modülü"
                placeholderEn="e.g. E-Commerce Module"
              />
              <div>
                <label className="block text-sm font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>{t('productPanel.labelMaxTickets')}</label>
                <input
                  type="number"
                  min="1"
                  max="10000"
                  value={formData.maxActiveTickets}
                  onChange={e => setFormData({ ...formData, maxActiveTickets: e.target.value })}
                  placeholder={t('productPanel.placeholderUnlimited')}
                  className="w-full rounded-lg border px-3 py-2 text-sm outline-none transition-all focus:ring-2"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
                <p className="mt-1 text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('productPanel.hintMaxTickets')}</p>
              </div>
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={e => setFormData({ ...formData, isActive: e.target.checked })}
                  className="h-4 w-4 rounded border-gray-300 text-primary-500 focus:ring-primary-500 cursor-pointer"
                />
                <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{t('productPanel.labelActive')}</span>
              </label>
            </div>
            <div className="flex justify-end gap-3 px-6 py-4 border-t" style={{ borderColor: 'var(--border-color)' }}>
              <Button
                variant="secondary"
                onClick={closeModal}
              >
                {t('productPanel.cancel')}
              </Button>
              <Button
                onClick={handleSave}
              >
                {t('productPanel.save')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
