import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { UserPlus, Search, X } from 'lucide-react';
import api from '../../services/api';
import AdminCreateUserModal from '../../components/AdminCreateUserModal';
import PaginationBar from '../../components/PaginationBar';

const ROLES = ['', 'CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER'];

/** Rol badge renkleri — AdminPanel ile tutarlı */
const roleBadgeStyle = (role) => {
  switch (role) {
    case 'AGENT_ADMIN': return { backgroundColor: 'rgba(139,92,246,0.15)', color: '#7c3aed' };
    case 'AGENT':       return { backgroundColor: 'rgba(59,130,246,0.15)',  color: '#1d4ed8' };
    case 'MANAGER':     return { backgroundColor: 'rgba(34,197,94,0.15)',   color: '#15803d' };
    case 'CUSTOMER':    return { backgroundColor: 'rgba(16,185,129,0.15)',  color: '#047857' };
    default:            return { backgroundColor: 'rgba(100,116,139,0.15)', color: '#475569' };
  }
};

/** Tarih formatlayıcı */
const formatDate = (isoString) => {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  });
};

export default function UserManagementPage() {
  const { t } = useTranslation();

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------
  const [users, setUsers]             = useState([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');

  const [search, setSearch]           = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [roleFilter, setRoleFilter]   = useState('');

  const [page, setPage]               = useState(0);
  const [size, setSize]               = useState(20);
  const [totalPages, setTotalPages]   = useState(0);
  const [totalItems, setTotalItems]   = useState(0);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [successMsg, setSuccessMsg]   = useState('');

  // -------------------------------------------------------------------------
  // Debounce — arama 300ms bekler
  // -------------------------------------------------------------------------
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Filtre değişince sayfayı sıfırla
  useEffect(() => { setPage(0); }, [debouncedSearch, roleFilter]);

  // -------------------------------------------------------------------------
  // Veri çekme
  // -------------------------------------------------------------------------
  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const params = new URLSearchParams({ page, size });
      if (debouncedSearch) params.set('search', debouncedSearch);
      if (roleFilter)      params.set('role',   roleFilter);

      const res = await api.get(`/users?${params}`);
      setUsers(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalItems(res.data.totalElements);
    } catch {
      setError(t('userManagement.errorLoad'));
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedSearch, roleFilter, t]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------
  const handleUserCreated = (newUser) => {
    setSuccessMsg(t('userManagement.success'));
    setTimeout(() => setSuccessMsg(''), 4000);
    // Listeyi yenile — yeni kullanıcı ilk sayfada görünsün
    setPage(0);
    fetchUsers();
  };

  const handleClearFilters = () => {
    setSearch('');
    setRoleFilter('');
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <>
      {/* Modal */}
      <AdminCreateUserModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onUserCreated={handleUserCreated}
      />

      {/* Sayfa başlığı */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('userManagement.title')}
          </h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {t('userManagement.subtitle')}
          </p>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer"
        >
          <UserPlus className="h-4 w-4" />
          {t('userManagement.createUser')}
        </button>
      </div>

      {/* Başarı mesajı */}
      {successMsg && (
        <div className="mb-4 rounded-lg px-4 py-3 text-sm font-medium bg-green-50 text-green-700 dark:bg-green-500/10 dark:text-green-400">
          {successMsg}
        </div>
      )}

      {/* Hata mesajı */}
      {error && (
        <div className="mb-4 rounded-lg px-4 py-3 text-sm font-medium bg-danger-50 text-danger-600 dark:bg-danger-500/10 dark:text-danger-400">
          {error}
        </div>
      )}

      {/* Tablo kartı */}
      <div
        className="rounded-xl border overflow-hidden"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        {/* Header + Filtreler */}
        <div
          className="px-6 py-4 border-b flex flex-wrap items-center justify-between gap-3"
          style={{ borderColor: 'var(--border-color)' }}
        >
          <span className="font-semibold text-sm" style={{ color: 'var(--text-primary)' }}>
            {t('userManagement.table.allUsers')}
            {totalItems > 0 && (
              <span className="ml-2 text-xs font-normal" style={{ color: 'var(--text-tertiary)' }}>
                ({totalItems})
              </span>
            )}
          </span>

          <div className="flex flex-wrap items-center gap-2">
            {/* Arama */}
            <div className="relative">
              <Search
                className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 pointer-events-none"
                style={{ color: 'var(--text-tertiary)' }}
              />
              <input
                type="text"
                placeholder={t('userManagement.table.searchPlaceholder')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="rounded-lg border pl-8 pr-3 py-1.5 text-xs outline-none focus:ring-2 w-52"
                style={{
                  backgroundColor: 'var(--bg-input)',
                  borderColor: 'var(--border-color)',
                  color: 'var(--text-primary)',
                  '--tw-ring-color': 'var(--ring-color)',
                }}
              />
              {search && (
                <button
                  onClick={() => setSearch('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer"
                  style={{ color: 'var(--text-tertiary)' }}
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>

            {/* Rol filtresi */}
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              className="rounded-lg border px-2.5 py-1.5 text-xs outline-none focus:ring-2 cursor-pointer"
              style={{
                backgroundColor: 'var(--bg-input)',
                borderColor: 'var(--border-color)',
                color: 'var(--text-primary)',
                '--tw-ring-color': 'var(--ring-color)',
              }}
            >
              <option value="">{t('admin.panel.allRoles')}</option>
              {ROLES.filter((r) => r).map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>

            {/* Filtreleri temizle */}
            {(search || roleFilter) && (
              <button
                onClick={handleClearFilters}
                className="rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-colors cursor-pointer hover:bg-danger-50 dark:hover:bg-danger-500/10"
                style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
              >
                {t('admin.panel.clear')}
              </button>
            )}
          </div>
        </div>

        {/* Tablo */}
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <div
                className="h-7 w-7 rounded-full border-[3px] animate-spin"
                style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }}
              />
            </div>
          ) : (
            <table className="w-full" style={{ tableLayout: 'fixed' }}>
              <colgroup>
                <col style={{ width: '22%' }} />
                <col style={{ width: '28%' }} />
                <col style={{ width: '15%' }} />
                <col style={{ width: '20%' }} />
                <col style={{ width: '15%' }} />
              </colgroup>
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  {[
                    t('userManagement.table.name'),
                    t('userManagement.table.email'),
                    t('userManagement.table.role'),
                    t('userManagement.table.username'),
                    t('userManagement.table.createdAt'),
                  ].map((h) => (
                    <th
                      key={h}
                      className="text-left px-4 py-3 text-xs font-semibold uppercase tracking-wider border-b"
                      style={{ color: 'var(--text-tertiary)', borderColor: 'var(--border-color)' }}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr
                    key={user.id}
                    style={{ borderBottom: '1px solid var(--border-color-light)' }}
                  >
                    {/* Ad Soyad */}
                    <td
                      className="px-4 py-3 text-sm font-semibold truncate"
                      style={{ color: 'var(--text-primary)' }}
                    >
                      {user.fullName}
                    </td>

                    {/* E-posta */}
                    <td
                      className="px-4 py-3 text-sm truncate"
                      style={{ color: 'var(--text-secondary)' }}
                    >
                      {user.email}
                    </td>

                    {/* Rol badge */}
                    <td className="px-4 py-3">
                      <span
                        className="inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold"
                        style={roleBadgeStyle(user.role)}
                      >
                        {user.role}
                      </span>
                    </td>

                    {/* Kullanıcı ID (Keycloak UUID kısaltılmış) */}
                    <td
                      className="px-4 py-3 text-xs font-mono truncate"
                      style={{ color: 'var(--text-tertiary)' }}
                      title={user.id}
                    >
                      {user.id ? user.id.split('-')[0] + '…' : '—'}
                    </td>

                    {/* Oluşturulma tarihi */}
                    <td
                      className="px-4 py-3 text-xs"
                      style={{ color: 'var(--text-secondary)' }}
                    >
                      {formatDate(user.createdAt)}
                    </td>
                  </tr>
                ))}

                {users.length === 0 && (
                  <tr>
                    <td
                      colSpan="5"
                      className="text-center py-12 text-sm"
                      style={{ color: 'var(--text-tertiary)' }}
                    >
                      {search || roleFilter
                        ? t('admin.panel.noUsersFiltered')
                        : t('userManagement.table.noUsers')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {/* Sayfalama */}
        <PaginationBar
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          size={size}
          onPageChange={setPage}
          onSizeChange={(s) => { setSize(s); setPage(0); }}
        />
      </div>
    </>
  );
}
