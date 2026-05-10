import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, X, ChevronDown, ChevronUp, Search, Settings2, Check } from 'lucide-react';
import api, { getAgentLimits, setAgentLimit } from '../../services/api';
import RateLimitConfigPanel from '../../components/RateLimitConfigPanel';
import SlaPolicyConfigPanel from '../../components/SlaPolicyConfigPanel';
import PaginationBar from '../../components/PaginationBar';

const VISIBLE_LIMIT = 3;
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const ROLES = ['', 'CUSTOMER', 'AGENT', 'AGENT_ADMIN'];

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
          {prod.name}
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
    const numVal = entry.useCustom ? parseInt(entry.value, 10) : null;
    if (entry.useCustom && (!numVal || numVal < 1)) {
      setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], error: '≥ 1' } }));
      return;
    }
    setLimits(prev => ({ ...prev, [productId]: { ...prev[productId], saving: true, error: '' } }));
    try {
      await setAgentLimit(user.id, productId, entry.useCustom, entry.useCustom ? numVal : null);
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
    <div className="mt-3 rounded-lg border overflow-hidden" style={{ borderColor: 'var(--border-color)' }}>
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
                {/* Ürün adı */}
                <td className="px-3 py-2 font-medium" style={{ color: 'var(--text-primary)' }}>{prod.name}</td>

                {/* Varsayılan limit */}
                <td className="px-3 py-2" style={{ color: 'var(--text-secondary)' }}>
                  {prod.maxActiveTickets ?? <span style={{ color: 'var(--text-tertiary)' }}>{t('admin.panel.agentLimitsUnlimited')}</span>}
                </td>

                {/* Toggle */}
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

                {/* Değer input */}
                <td className="px-3 py-2">
                  <input
                    type="number"
                    min="1"
                    disabled={!entry.useCustom}
                    value={entry.value}
                    onChange={e => handleValue(prod.id, e.target.value)}
                    placeholder="—"
                    className="w-20 rounded border px-2 py-1 text-xs outline-none focus:ring-2 disabled:opacity-40"
                    style={{ backgroundColor: 'var(--bg-input)', borderColor: entry.error ? '#ef4444' : 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                  />
                  {entry.error && <span className="ml-1 text-[10px]" style={{ color: '#ef4444' }}>{entry.error}</span>}
                </td>

                {/* Kaydet butonu */}
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
  );
}

export default function AdminPanel() {
  const { t } = useTranslation();

  const [users, setUsers]               = useState([]);
  const [products, setProducts]         = useState([]);
  const [loading, setLoading]           = useState(true);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [error, setError]               = useState('');

  const [search, setSearch]   = useState('');
  const [roleFilter, setRoleFilter] = useState('');

  const [page, setPage]             = useState(0);
  const [size, setSize]             = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  // Hangi agent'ın limit paneli açık
  const [expandedLimitUserId, setExpandedLimitUserId] = useState(null);

  const [debouncedSearch, setDebouncedSearch] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => { setPage(0); }, [debouncedSearch, roleFilter]);

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({ page, size });
      if (debouncedSearch) params.set('search', debouncedSearch);
      if (roleFilter)      params.set('role',   roleFilter);

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
  }, [page, size, debouncedSearch, roleFilter, t]);

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
      alert(t('admin.panel.alertAssign'));
      return;
    }
    try {
      const res = await api.post(`/users/${userId}/products/${selectedProductId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || t('admin.panel.errorAssign'));
    }
  };

  const handleRemoveProduct = async (userId, productId) => {
    if (!window.confirm(t('admin.panel.confirmRemove'))) return;
    try {
      const res = await api.delete(`/users/${userId}/products/${productId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || t('admin.panel.errorRemove'));
    }
  };

  const roleBadgeStyle = (role) => {
    switch (role) {
      case 'AGENT_ADMIN': return 'bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-300';
      case 'AGENT':       return 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300';
      case 'CUSTOMER':    return 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300';
      default:            return 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-300';
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
        <div className="px-6 py-4 border-b flex flex-wrap items-center justify-between gap-3"
          style={{ borderColor: 'var(--border-color)' }}>
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('admin.panel.userProducts')}
            {totalItems > 0 && (
              <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
                {t('admin.panel.usersCount', { count: totalItems })}
              </span>
            )}
          </span>

          <div className="flex flex-wrap items-center gap-2">
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 pointer-events-none"
                style={{ color: 'var(--text-tertiary)' }} />
              <input
                type="text"
                placeholder={t('admin.panel.searchPlaceholder')}
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="rounded-lg border pl-8 pr-3 py-1.5 text-xs outline-none focus:ring-2 w-52"
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

            {/* Role filter */}
            <select
              value={roleFilter}
              onChange={e => setRoleFilter(e.target.value)}
              className="rounded-lg border px-2.5 py-1.5 text-xs outline-none focus:ring-2 cursor-pointer"
              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
            >
              <option value="">{t('admin.panel.allRoles')}</option>
              {ROLES.filter(r => r).map(r => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>

            {/* Clear filters */}
            {(search || roleFilter) && (
              <button
                onClick={() => { setSearch(''); setRoleFilter(''); }}
                className="rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              >
                {t('admin.panel.clear')}
              </button>
            )}
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <div className="h-7 w-7 rounded-full border-[3px] animate-spin"
                style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
            </div>
          ) : (
            <table className="w-full" style={{ tableLayout: 'fixed' }}>
              <colgroup>
                <col style={{ width: '13%' }} />
                <col style={{ width: '18%' }} />
                <col style={{ width: '10%' }} />
                <col style={{ width: '28%' }} />
                <col style={{ width: '21%' }} />
                <col style={{ width: '10%' }} />
              </colgroup>
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  {[
                    t('admin.panel.colName'),
                    t('admin.panel.colEmail'),
                    t('admin.panel.colRole'),
                    t('admin.panel.colAuthorized'),
                    t('admin.panel.colAssign'),
                    t('admin.panel.agentLimits'),
                  ].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {users.map(user => {
                  const isAgent = user.role === 'AGENT' || user.role === 'AGENT_ADMIN';
                  const limitOpen = expandedLimitUserId === user.id;
                  return (
                    <React.Fragment key={user.id}>
                      <tr key={user.id} style={{ borderBottom: limitOpen ? 'none' : '1px solid var(--border-color-light)' }}>
                        <td className="px-4 py-3 text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
                          {user.fullName}
                        </td>
                        <td className="px-4 py-3 text-sm truncate" style={{ color: 'var(--text-secondary)' }}>
                          {user.email}
                        </td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold ${roleBadgeStyle(user.role)}`}>
                            {user.role}
                          </span>
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
                              className="flex-1 rounded-lg border px-2 py-1.5 text-xs outline-none transition-all focus:ring-2 cursor-pointer"
                              style={{ backgroundColor: 'var(--bg-input)', borderColor: 'var(--border-color)', color: 'var(--text-primary)', '--tw-ring-color': 'var(--ring-color)' }}
                              onChange={(e) => setSelectedProductId(e.target.value)}
                              value={selectedProductId}
                            >
                              <option value="">{t('admin.panel.selectProduct')}</option>
                              {products
                                .filter(p => !(user.authorizedProducts || []).some(ap => ap.id === p.id))
                                .map(p => (
                                  <option key={p.id} value={p.id}>{p.name}</option>
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
                        {/* Limit butonu — sadece agent rolleri için */}
                        <td className="px-4 py-3">
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
                        <tr key={`${user.id}-limits`} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
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
                      {search || roleFilter ? t('admin.panel.noUsersFiltered') : t('admin.panel.noUsers')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination */}
        <PaginationBar
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          size={size}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0); }}
        />
      </div>

      <RateLimitConfigPanel />
      <SlaPolicyConfigPanel />
    </>
  );
}
