import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, X, ChevronDown, ChevronUp, Settings2, Check } from 'lucide-react';
import api, { getAgentLimits, setAgentLimit } from '../../services/api';
import { useToast } from '../../context/ToastContext';
import PaginationBar from '../../components/PaginationBar';
import SortableTh from '../../components/SortableTh';
import { useColumnResize } from '../../hooks/useColumnResize';
import MultiSelectFilter from '../../components/filters/MultiSelectFilter';
import FilterSearchInput from '../../components/filters/FilterSearchInput';
import ClearFiltersButton from '../../components/filters/ClearFiltersButton';
import { useUrlState } from '../../hooks/useUrlState';
import { rolesOf, roleBadgeStyle } from '../../utils/userRoles';
import { localizedName } from '../../utils/localizedName';

const VISIBLE_LIMIT = 3;
const PAGE_SIZE_OPTIONS = [10, 20, 50];
// ADMIN/MANAGER bu panelde gösterilmez (tüm ürünlere erişimleri var) — filtre seçeneklerinde de yok.
const ROLES = ['CUSTOMER', 'AGENT', 'LEAD_AGENT'];

// Operasyonel ajan rolleri — bilet limiti olan kullanıcılar (lead dâhil).
const AGENT_ROLES = ['AGENT', 'LEAD_AGENT'];

// Kullanıcının rollerinden herhangi biri operasyonel ajan mı? (bilet limiti UI'ı bu kullanıcılarda gösterilir)
const hasAgentRole = (user) => rolesOf(user).some((r) => AGENT_ROLES.includes(r));

function ProductChips({ products, onRemove, t }) {
  const [expanded, setExpanded] = useState(false);

  if (!products || products.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.noProducts')}</span>;
  }

  const visible = expanded ? products : products.slice(0, VISIBLE_LIMIT);
  const hiddenCount = products.length - VISIBLE_LIMIT;

  return (
    <div className="flex flex-wrap gap-1.5 items-center">
      {visible.map(prod => (
        <span
          key={prod.id}
          className="inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-primary)' }}
        >
          {localizedName(prod)}
          <button
            onClick={() => onRemove(prod.id)}
            title="Remove"
            className="ml-0.5 rounded hover:text-danger-500 transition-colors cursor-pointer"
            style={{ color: 'var(--text-tertiary)' }}
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}

      {!expanded && hiddenCount > 0 && (
        <button
          onClick={() => setExpanded(true)}
          className="inline-flex items-center gap-0.5 rounded-md border px-2 py-0.5 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
        >
          {t('admin.panel.moreProducts', { count: hiddenCount })}
          <ChevronDown className="h-3 w-3" />
        </button>
      )}

      {expanded && products.length > VISIBLE_LIMIT && (
        <button
          onClick={() => setExpanded(false)}
          className="inline-flex items-center gap-0.5 rounded-md border px-2 py-0.5 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
        >
          {t('admin.panel.showLess')}
          <ChevronUp className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}

/**
 * Bir agent'ın ürün bazlı bilet limit override'larını yöneten inline panel.
 * Her yetkili ürün için: varsayılan limit gösterilir, özel limit toggle + input ile ayarlanır.
 */
function AgentLimitsPanel({ user, t }) {
  const [limits, setLimits]   = useState(null);
  const [loadErr, setLoadErr] = useState('');

  useEffect(() => {
    if (!user) return;
    let cancelled = false;

    getAgentLimits(user.id)
      .then(res => {
        if (cancelled) return;
        const map = {};
        (res.data || []).forEach(l => {
          map[l.productId] = {
            useCustom: l.useCustomLimit,
            value:     l.maxActiveTickets ?? '',
            saved:     false,
            saving:    false,
            error:     '',
          };
        });
        (user.authorizedProducts || []).forEach(p => {
          if (!map[p.id]) {
            map[p.id] = { useCustom: false, value: '', saved: false, saving: false, error: '' };
          }
        });
        setLimits(map);
      })
      .catch(() => {
        if (!cancelled) setLoadErr(t('admin.panel.agentLimitsErrorLoad'));
      });

    return () => { cancelled = true; };
  }, [user, t]);

  const handleToggle = (productId, checked) => {
    setLimits(prev => ({
      ...prev,
      [productId]: { ...prev[productId], useCustom: checked, saved: false, error: '' },
    }));
  };

  const handleValue = (productId, val) => {
    setLimits(prev => ({
      ...prev,
      [productId]: { ...prev[productId], value: val, saved: false, error: '' },
    }));
  };

  const handleSave = async (productId) => {
    const entry = limits[productId];
    const hasValue = entry.useCustom && String(entry.value).trim() !== '';
    const numVal = hasValue ? parseInt(entry.value, 10) : null;
    // Empty value with custom enabled means "unlimited" (null). Only validate when a value is entered.
    if (hasValue && (Number.isNaN(numVal) || numVal < 1)) {
      setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], error: '≥ 1' } }));
      return;
    }
    if (hasValue && numVal > 10000) {
      setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], error: '≤ 10000' } }));
      return;
    }
    setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], saving: true, error: '' } }));
    try {
      await setAgentLimit(user.id, productId, entry.useCustom, numVal);
      setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], saving: false, saved: true } }));
      setTimeout(() => setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], saved: false } })), 2000);
    } catch {
      setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], saving: false, error: t('admin.panel.agentLimitsErrorSave') } }));
    }
  };

  const authorizedProducts = user?.authorizedProducts || [];

  if (loadErr) {
    return <p className="text-xs px-1 py-2" style={{ color: 'var(--text-danger, #ef4444)' }}>{loadErr}</p>;
  }

  if (!limits) {
    return (
      <div className="flex items-center gap-2 py-2 px-1">
        <div className="h-4 w-4 rounded-full border-2 animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
        <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>Yükleniyor…</span>
      </div>
    );
  }

  if (authorizedProducts.length === 0) {
    return <p className="text-xs px-1 py-2" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsNoProducts')}</p>;
  }

  return (
    <>
      {/* Mobile: card list per product */}
      <ul className="lg:hidden mt-3 space-y-2">
        {authorizedProducts.map(prod => {
          const entry = limits[prod.id] || { useCustom: false, value: '', saving: false, saved: false, error: '' };
          return (
            <li key={prod.id} className="rounded-lg border p-3" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
              <div className="flex items-center justify-between gap-2 mb-2">
                <span className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>{localizedName(prod)}</span>
                <span className="text-[11px] whitespace-nowrap" style={{ color: 'var(--text-tertiary)' }}>
                  {t('admin.panel.agentLimitsDefault')}: {prod.maxActiveTickets ?? t('admin.panel.agentLimitsUnlimited')}
                </span>
              </div>
              <label className="flex items-center gap-2 cursor-pointer select-none mb-2">
                <input
                  type="checkbox"
                  checked={entry.useCustom}
                  onChange={e => handleToggle(prod.id, e.target.checked)}
                  className="rounded"
                />
                <span className="text-xs" style={{ color: 'var(--text-secondary)' }}>{t('admin.panel.agentLimitsCustom')}: {entry.useCustom ? 'Açık' : 'Kapalı'}</span>
              </label>
              <div className="flex items-center gap-2 mb-2">
                <input
                  type="number"
                  min="1"
                  max="10000"
                  disabled={!entry.useCustom}
                  value={entry.value}
                  onChange={e => handleValue(prod.id, e.target.value)}
                  placeholder={entry.useCustom ? t('admin.panel.agentLimitsUnlimited') : '—'}
                  className="flex-1 rounded border px-2 py-1.5 text-xs outline-none focus:ring-2 disabled:opacity-40"
                  style={{ backgroundColor: 'var(--bg-input)', borderColor: entry.error ? '#ef4444' : 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                />
                {entry.error && <span className="text-[10px]" style={{ color: '#ef4444' }}>{entry.error}</span>}
              </div>
              <button
                onClick={() => handleSave(prod.id)}
                disabled={entry.saving}
                className="inline-flex w-full items-center justify-center gap-1 rounded px-2.5 py-1.5 text-xs font-semibold text-white transition-colors cursor-pointer disabled:opacity-50"
                style={{ backgroundColor: entry.saved ? '#22c55e' : '#3b82f6' }}
              >
                {entry.saved
                  ? <><Check className="h-3 w-3" />{t('admin.panel.agentLimitsSaved')}</>
                  : entry.saving
                    ? '…'
                    : t('admin.panel.agentLimitsSave')}
              </button>
            </li>
          );
        })}
      </ul>

      {/* Desktop: table */}
      <div className="hidden lg:block mt-3 rounded-lg border overflow-hidden" style={{ borderColor: 'var(--border-color)' }}>
        <table className="w-full text-xs">
          <thead>
            <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
              <th className="text-left px-3 py-2 font-semibold" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsProduct')}</th>
              <th className="text-left px-3 py-2 font-semibold" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsDefault')}</th>
              <th className="text-left px-3 py-2 font-semibold" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsCustom')}</th>
              <th className="text-left px-3 py-2 font-semibold" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsValue')}</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {authorizedProducts.map(prod => {
              const entry = limits[prod.id] || { useCustom: false, value: '', saving: false, saved: false, error: '' };
              return (
                <tr key={prod.id} style={{ borderTop: '1px solid var(--border-color-light)' }}>
                  <td className="px-3 py-2 font-medium" style={{ color: 'var(--text-primary)' }}>{localizedName(prod)}</td>
                  <td className="px-3 py-2" style={{ color: 'var(--text-secondary)' }}>
                    {prod.maxActiveTickets ?? <span style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsUnlimited')}</span>}
                  </td>
                  <td className="px-3 py-2">
                    <label className="inline-flex items-center gap-1.5 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        checked={entry.useCustom}
                        onChange={e => handleToggle(prod.id, e.target.checked)}
                        className="rounded"
                      />
                      <span style={{ color: 'var(--text-secondary)' }}>{entry.useCustom ? 'Açık' : 'Kapalı'}</span>
                    </label>
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      min="1"
                      max="10000"
                      disabled={!entry.useCustom}
                      value={entry.value}
                      onChange={e => handleValue(prod.id, e.target.value)}
                      placeholder={entry.useCustom ? t('admin.panel.agentLimitsUnlimited') : '—'}
                      className="w-20 rounded border px-2 py-1 text-xs outline-none focus:ring-2 disabled:opacity-40"
                      style={{ backgroundColor: 'var(--bg-input)', borderColor: entry.error ? '#ef4444' : 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                    />
                    {entry.error && <span className="ml-1 text-[10px]" style={{ color: '#ef4444' }}>{entry.error}</span>}
                  </td>
                  <td className="px-3 py-2">
                    <button
                      onClick={() => handleSave(prod.id)}
                      disabled={entry.saving}
                      className="inline-flex items-center gap-1 rounded px-2.5 py-1 text-xs font-semibold text-white transition-colors cursor-pointer disabled:opacity-50"
                      style={{ backgroundColor: entry.saved ? '#22c55e' : '#3b82f6' }}
                    >
                      {entry.saved
                        ? <><Check className="h-3 w-3" />{t('admin.panel.agentLimitsSaved')}</>
                        : entry.saving
                          ? '…'
                          : t('admin.panel.agentLimitsSave')}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}

// Sürüklenebilir sütun varsayılan genişlikleri (px). Son sütun esner.
const COL_WIDTHS = { name: 150, email: 210, role: 110, authorized: 480, assign: 360, agentLimits: 120 };
const COL_ORDER = ['name', 'email', 'role', 'authorized', 'assign', 'agentLimits'];

export default function AdminPanel() {
  const { t } = useTranslation();
  const toast = useToast();
  const { tableWidth, handleFor, renderColgroup } = useColumnResize(COL_WIDTHS, COL_ORDER, 'colw:admin-panel');

  const [users, setUsers]               = useState([]);
  const [products, setProducts]         = useState([]);
  const [loading, setLoading]           = useState(true);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [error, setError]               = useState('');

  // Arama + rol/ürün filtresi + sayfalama + sıralama URL'de tutulur (F5 / yer imi / link paylaşımı korur).
  const { str, num, arr, setParams, searchParams } = useUrlState();
  const search        = str('search');
  const roleFilter    = arr('role');
  const productFilter = arr('productId');
  const page    = num('page', 0);
  const size    = num('size', 20);
  const sortBy  = str('sortBy', 'name');
  const sortDir = str('sortDir', 'asc');
  const setSearch        = (v) => setParams({ search: v });
  const setRoleFilter    = (v) => setParams({ role: v });
  const setProductFilter = (v) => setParams({ productId: v });
  const setPage = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize = (v) => setParams({ size: v === 20 ? '' : v });

  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  // Hangi agent'ın limit paneli açık
  const [expandedLimitUserId, setExpandedLimitUserId] = useState(null);

  // Sütun başlığına tıklanınca sıralamayı çevirir (bilet tablolarındaki toggleSort ile aynı).
  const toggleSort = (field) => {
    const nextDir = sortBy === field ? (sortDir === 'asc' ? 'desc' : 'asc') : 'asc';
    setParams({ sortBy: field === 'name' ? '' : field, sortDir: nextDir === 'asc' ? '' : nextDir });
  };

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({ page, size, sortBy, sortDir });
      if (search) params.set('search', search);
      roleFilter.forEach((r) => params.append('role', r));
      productFilter.forEach((pid) => params.append('productId', pid));
      // ADMIN/MANAGER kullanıcılar (tüm ürünlere erişimli) ürün-erişim panelinde listelenmez.
      params.set('excludeGlobalRoles', 'true');

      const res = await api.get(`/users?${params}`);
      setUsers(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalItems(res.data.totalElements);
    } catch (err) {
      console.error('Could not load users:', err);
      setError(t('admin.panel.errorLoad'));
    } finally {
      setLoading(false);
    }
    // searchParams tüm filtre/sayfa/sıralama paramlarını kapsar — kimliği yalnızca URL değişince değişir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, t]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  useEffect(() => {
    api.get('/products')
      .then(res => setProducts(res.data))
      .catch(err => console.error('Could not load products:', err));
  }, []);

  const handleAssignProduct = async (userId) => {
    if (!selectedProductId) {
      toast.error(t('admin.panel.alertAssign'));
      return;
    }
    try {
      const res = await api.post(`/users/${userId}/products/${selectedProductId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      toast.error(err.response?.data?.message || t('admin.panel.errorAssign'));
    }
  };

  const handleRemoveProduct = async (userId, productId) => {
    if (!window.confirm(t('admin.panel.confirmRemove'))) return;
    try {
      const res = await api.delete(`/users/${userId}/products/${productId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      toast.error(err.response?.data?.message || t('admin.panel.errorRemove'));
    }
  };

  return (
    <>
      <div className="mb-6">
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>{t('admin.panel.title')}</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>{t('admin.panel.subtitle')}</p>
      </div>

      {error && (
        <div className="rounded-lg px-4 py-3 mb-5 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      <div className="rounded-xl border overflow-hidden" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)', boxShadow: 'var(--shadow-sm)' }}>

        {/* Header + Filters */}
        <div className="px-4 py-4 border-b flex flex-col sm:flex-row sm:flex-wrap sm:items-center sm:justify-between gap-3 sm:px-6"
          style={{ borderColor: 'var(--border-color)' }}>
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('admin.panel.userProducts')}
            {totalItems > 0 && (
              <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
                {t('admin.panel.usersCount', { count: totalItems })}
              </span>
            )}
          </span>

          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <FilterSearchInput
              value={search}
              onChange={setSearch}
              placeholder={t('admin.panel.searchPlaceholder')}
              width="13rem"
              debounceMs={300}
            />
            <MultiSelectFilter
              values={roleFilter}
              onChange={setRoleFilter}
              placeholder={t('admin.panel.allRoles')}
              options={ROLES.map((r) => ({ value: r, label: r }))}
            />
            <MultiSelectFilter
              values={productFilter}
              onChange={setProductFilter}
              placeholder={t('admin.panel.allProducts')}
              options={products.map((p) => ({ value: String(p.id), label: localizedName(p) }))}
            />
            {(search || roleFilter.length > 0 || productFilter.length > 0) && (
              <ClearFiltersButton onClick={() => setParams({ search: '', role: [], productId: [] })} />
            )}
          </div>
        </div>

        {/* Loading state shared by both layouts */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-7 w-7 rounded-full border-[3px] animate-spin"
              style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
          </div>
        ) : (
          <>
            {/* Mobile: card list */}
            <ul className="lg:hidden p-4 space-y-3">
              {users.map(user => {
                const isAgent = hasAgentRole(user);
                const limitOpen = expandedLimitUserId === user.id;
                return (
                  <li key={user.id} className="rounded-xl border p-4" style={{ backgroundColor: 'var(--bg-surface)', borderColor: 'var(--border-color)' }}>
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>{user.fullName}</span>
                      {rolesOf(user).length ? (
                        <div className="flex flex-wrap items-center justify-end gap-1">
                          {rolesOf(user).map((r) => (
                            <span
                              key={r}
                              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold whitespace-nowrap"
                              style={roleBadgeStyle(r)}
                            >
                              {r}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span
                          className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-medium"
                          style={{ backgroundColor: 'rgba(100,116,139,0.1)', color: 'var(--text-tertiary)', border: '1px dashed var(--border-color)' }}
                        >
                          —
                        </span>
                      )}
                    </div>
                    <p className="text-xs break-all mb-3" style={{ color: 'var(--text-secondary)' }}>{user.email}</p>

                    <div className="mb-3">
                      <p className="text-[11px] uppercase tracking-wide mb-1.5" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.colAuthorized')}</p>
                      <ProductChips
                        products={user.authorizedProducts}
                        onRemove={(productId) => handleRemoveProduct(user.id, productId)}
                        t={t}
                      />
                    </div>

                    <div className="mb-3">
                      <p className="text-[11px] uppercase tracking-wide mb-1.5" style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.colAssign')}</p>
                      <div className="flex flex-col gap-2">
                        <select
                          className="w-full rounded-lg border px-2 py-1.5 text-xs outline-none transition-all focus:ring-2 cursor-pointer"
                          style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                          onChange={(e) => setSelectedProductId(e.target.value)}
                          value={selectedProductId}
                        >
                          <option value="">{t('admin.panel.selectProduct')}</option>
                          {products
                            .filter(p => !(user.authorizedProducts || []).some(ap => ap.id === p.id))
                            .map(p => (
                              <option key={p.id} value={p.id}>{localizedName(p)}</option>
                            ))
                          }
                        </select>
                        <button
                          className="inline-flex w-full items-center justify-center gap-1 rounded-lg px-3 py-2 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                          onClick={() => handleAssignProduct(user.id)}
                        >
                          <Plus className="h-3 w-3" />
                          {t('admin.panel.addProduct')}
                        </button>
                      </div>
                    </div>

                    {isAgent && (
                      <div className="pt-3 border-t" style={{ borderColor: 'var(--border-color-light)' }}>
                        <button
                          onClick={() => setExpandedLimitUserId(limitOpen ? null : user.id)}
                          className="inline-flex w-full items-center justify-center gap-1 rounded-lg border px-2.5 py-2 text-xs font-medium transition-colors cursor-pointer"
                          style={{
                            backgroundColor: limitOpen ? 'var(--bg-surface-secondary)' : 'var(--bg-input)',
                            borderColor: limitOpen ? 'var(--primary-500, #3b82f6)' : 'var(--border-color)',
                            color: limitOpen ? 'var(--primary-500, #3b82f6)' : 'var(--text-secondary)',
                          }}
                        >
                          <Settings2 className="h-3 w-3" />
                          {t('admin.panel.agentLimits')}
                        </button>
                        {limitOpen && (
                          <div className="mt-3 rounded-lg border p-3" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}>
                            <div className="flex items-start justify-between gap-2 mb-2">
                              <div className="min-w-0">
                                <p className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>
                                  {t('admin.panel.agentLimitsTitle', { name: user.fullName })}
                                </p>
                                <p className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>
                                  {t('admin.panel.agentLimitsSubtitle')}
                                </p>
                              </div>
                              <button
                                onClick={() => setExpandedLimitUserId(null)}
                                className="rounded p-1 flex-shrink-0 transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                                style={{ color: 'var(--text-tertiary)' }}
                              >
                                <X className="h-4 w-4" />
                              </button>
                            </div>
                            <AgentLimitsPanel user={user} t={t} />
                          </div>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
              {users.length === 0 && (
                <li className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                  {search || roleFilter.length > 0 ? t('admin.panel.noUsersFiltered') : t('admin.panel.noUsers')}
                </li>
              )}
            </ul>

            {/* Desktop: table */}
            <div className="hidden lg:block overflow-x-auto">
              <table className="w-full resizable-table" style={{ tableLayout: 'fixed', minWidth: `${tableWidth}px` }}>
              {renderColgroup()}
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <SortableTh field="name"  label={t('admin.panel.colName')}  sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('name')} />
                  <SortableTh field="email" label={t('admin.panel.colEmail')} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('email')} />
                  <SortableTh field="role"  label={t('admin.panel.colRole')}  sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('role')} />
                  <SortableTh field="authorized" label={t('admin.panel.colAuthorized')} resizeHandle={handleFor('authorized')} />
                  <SortableTh field="assign"     label={t('admin.panel.colAssign')} resizeHandle={handleFor('assign')} />
                  <SortableTh field="agentLimits" label={t('admin.panel.agentLimits')} align="right" className="pr-8" />
                </tr>
              </thead>
              <tbody>
                {users.map(user => {
                  const isAgent = hasAgentRole(user);
                  const limitOpen = expandedLimitUserId === user.id;
                  return (
                    <React.Fragment key={user.id}>
                      <tr key={user.id} className={limitOpen ? 'is-open' : undefined}>
                        <td className="px-4 py-3 text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                          {user.fullName}
                        </td>
                        <td className="px-4 py-3 text-sm truncate" style={{ color: 'var(--text-secondary)' }}>
                          {user.email}
                        </td>
                        <td className="px-4 py-3">
                          {rolesOf(user).length ? (
                            <div className="flex flex-wrap items-center gap-1">
                              {rolesOf(user).map((r) => (
                                <span
                                  key={r}
                                  className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold"
                                  style={roleBadgeStyle(r)}
                                >
                                  {r}
                                </span>
                              ))}
                            </div>
                          ) : (
                            <span
                              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-medium"
                              style={{ backgroundColor: 'rgba(100,116,139,0.1)', color: 'var(--text-tertiary)', border: '1px dashed var(--border-color)' }}
                            >
                              —
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 align-top">
                          <ProductChips
                            products={user.authorizedProducts}
                            onRemove={(productId) => handleRemoveProduct(user.id, productId)}
                            t={t}
                          />
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex gap-2">
                            <select
                              className="flex-1 min-w-0 rounded-lg border px-2 py-1.5 text-xs outline-none transition-all focus:ring-2 cursor-pointer"
                              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                              onChange={(e) => setSelectedProductId(e.target.value)}
                              value={selectedProductId}
                            >
                              <option value="">{t('admin.panel.selectProduct')}</option>
                              {products
                                .filter(p => !(user.authorizedProducts || []).some(ap => ap.id === p.id))
                                .map(p => (
                                  <option key={p.id} value={p.id}>{localizedName(p)}</option>
                                ))
                              }
                            </select>
                            <button
                              className="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
                              onClick={() => handleAssignProduct(user.id)}
                            >
                              <Plus className="h-3 w-3" />
                              {t('admin.panel.addProduct')}
                            </button>
                          </div>
                        </td>
                        {/* Limit butonu — sadece agent rolleri için. Sağa yaslı,
                            kenardan biraz boşluklu (pr-6). */}
                        <td className="py-3 pl-4 pr-6 text-right">
                          {isAgent ? (
                            <button
                              onClick={() => setExpandedLimitUserId(limitOpen ? null : user.id)}
                              title={t('admin.panel.agentLimits')}
                              className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer"
                              style={{
                                backgroundColor: limitOpen ? 'var(--bg-surface-secondary)' : 'var(--bg-input)',
                                borderColor: limitOpen ? 'var(--primary-500, #3b82f6)' : 'var(--border-color)',
                                color: limitOpen ? 'var(--primary-500, #3b82f6)' : 'var(--text-secondary)',
                              }}
                            >
                              <Settings2 className="h-3 w-3" />
                              {t('admin.panel.agentLimits')}
                            </button>
                          ) : (
                            <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>—</span>
                          )}
                        </td>
                      </tr>

                      {/* Inline limit paneli */}
                      {limitOpen && (
                        <tr key={`${user.id}-limits`}>
                          <td colSpan="6" className="px-6 pb-4 pt-0">
                            <div className="rounded-lg border p-4" style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)' }}>
                              <div className="flex items-center justify-between mb-3">
                                <div>
                                  <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                                    {t('admin.panel.agentLimitsTitle', { name: user.fullName })}
                                  </p>
                                  <p className="text-xs mt-0.5" style={{ color: 'var(--text-tertiary)' }}>
                                    {t('admin.panel.agentLimitsSubtitle')}
                                  </p>
                                </div>
                                <button
                                  onClick={() => setExpandedLimitUserId(null)}
                                  className="rounded p-1 transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                                  style={{ color: 'var(--text-tertiary)' }}
                                >
                                  <X className="h-4 w-4" />
                                </button>
                              </div>
                              <AgentLimitsPanel user={user} t={t} />
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
                {users.length === 0 && (
                  <tr>
                    <td colSpan="6" className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                      {search || roleFilter.length > 0 ? t('admin.panel.noUsersFiltered') : t('admin.panel.noUsers')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            </div>
          </>
        )}

        {/* Pagination */}
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
  );
}
