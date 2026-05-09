import { useState, useEffect, useCallback } from 'react';
import { Plus, X, ChevronDown, ChevronUp, Search } from 'lucide-react';
import api from '../../services/api';
import RateLimitConfigPanel from '../../components/RateLimitConfigPanel';
import PaginationBar from '../../components/PaginationBar';

const VISIBLE_LIMIT = 3;
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const ROLES = ['', 'CUSTOMER', 'AGENT', 'AGENT_ADMIN'];

function ProductChips({ products, onRemove }) {
  const [expanded, setExpanded] = useState(false);

  if (!products || products.length === 0) {
    return <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>No products</span>;
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
          +{hiddenCount} more
          <ChevronDown className="h-3 w-3" />
        </button>
      )}

      {expanded && products.length > VISIBLE_LIMIT && (
        <button
          onClick={() => setExpanded(false)}
          className="inline-flex items-center gap-0.5 rounded-md border px-2 py-0.5 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
          style={{ backgroundColor: 'var(--bg-surface-secondary)', borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
        >
          Show less
          <ChevronUp className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}

export default function AdminPanel() {
  const [users, setUsers]               = useState([]);
  const [products, setProducts]         = useState([]);
  const [loading, setLoading]           = useState(true);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [error, setError]               = useState('');

  // Filters
  const [search, setSearch]   = useState('');
  const [roleFilter, setRoleFilter] = useState('');

  // Pagination
  const [page, setPage]             = useState(0);
  const [size, setSize]             = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  // Debounced search
  const [debouncedSearch, setDebouncedSearch] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  // Reset to page 0 when filters change
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
      setError('An error occurred while loading users.');
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedSearch, roleFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // Products are loaded once
  useEffect(() => {
    api.get('/products')
      .then(res => setProducts(res.data))
      .catch(err => console.error('Could not load products:', err));
  }, []);

  const handleAssignProduct = async (userId) => {
    if (!selectedProductId) {
      alert('Please select a product to assign.');
      return;
    }
    try {
      const res = await api.post(`/users/${userId}/products/${selectedProductId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not assign product.');
    }
  };

  const handleRemoveProduct = async (userId, productId) => {
    if (!window.confirm('Are you sure you want to remove this product authorization?')) return;
    try {
      const res = await api.delete(`/users/${userId}/products/${productId}`);
      setUsers(users.map(u => u.id === userId ? res.data : u));
    } catch (err) {
      alert(err.response?.data?.message || 'Could not remove product authorization.');
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
        <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>Admin Panel</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>Manage user product authorizations.</p>
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
            User & Product Assignments
            {totalItems > 0 && (
              <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
                ({totalItems} users)
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
                placeholder="Search name or email…"
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
              <option value="">All Roles</option>
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
                Clear
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
                <col style={{ width: '14%' }} />
                <col style={{ width: '20%' }} />
                <col style={{ width: '11%' }} />
                <col style={{ width: '31%' }} />
                <col style={{ width: '24%' }} />
              </colgroup>
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  {['Name', 'Email', 'Role', 'Authorized Products', 'Assign Product'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {users.map(user => (
                  <tr key={user.id} style={{ borderBottom: '1px solid var(--border-color-light)' }}>
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
                          <option value="">Select…</option>
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
                          Add
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr>
                    <td colSpan="5" className="text-center py-12 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                      {search || roleFilter ? 'No users match the current filters.' : 'No users found.'}
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
    </>
  );
}
