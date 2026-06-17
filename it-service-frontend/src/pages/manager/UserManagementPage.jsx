import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { UserPlus, ShieldCheck, UserCheck, UserX, BarChart3 } from 'lucide-react';
import api, { updateUserStatus } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import AdminCreateUserModal from '../../components/AdminCreateUserModal';
import EditRoleModal from '../../components/EditRoleModal';
import PaginationBar from '../../components/PaginationBar';
import SortableTh from '../../components/SortableTh';
import ListLoadingOverlay from '../../components/ListLoadingOverlay';
import { useColumnResize } from '../../hooks/useColumnResize';
import { useUrlState } from '../../hooks/useUrlState';
import MultiSelectFilter from '../../components/filters/MultiSelectFilter';
import FilterSearchInput from '../../components/filters/FilterSearchInput';
import ClearFiltersButton from '../../components/filters/ClearFiltersButton';
import { rolesOf, roleBadgeStyle } from '../../utils/userRoles';
import { formatDate } from '../../utils/dateFormat';

const ROLES = ['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER'];

// Sürüklenebilir sütun varsayılan genişlikleri (px). Son sütun (actions) esner.
const COL_WIDTHS = { name: 200, email: 260, role: 130, username: 180, createdAt: 140, actions: 110 };
const COL_ORDER = ['name', 'email', 'role', 'username', 'createdAt', 'actions'];

export default function UserManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { tableWidth, handleFor, renderColgroup } = useColumnResize(COL_WIDTHS, COL_ORDER, 'colw:users');
  const { user: currentUser, isAdmin, isManager } = useAuth();
  const toast = useToast();

  // Rol düzenleme ve aktif/pasif alma yalnızca ADMIN yetkisidir. MANAGER bu sayfayı
  // salt-okunur görür (oversight rolü — yazma yetkisi yok).
  const canManageUsers = isAdmin;

  // Performans chart'larını görüntüleme oversight yetkisidir — ADMIN ve MANAGER.
  const canViewCharts = isAdmin || isManager;

  // Buton yalnızca chart'ı olan rollerde anlamlı: AGENT/LEAD_AGENT (ajan dashboard'u) veya
  // CUSTOMER (müşteri dashboard'u). Yalnızca admin/manager olan kullanıcıların dashboard'u yok.
  const DASHBOARD_ROLES = ['CUSTOMER', 'AGENT', 'LEAD_AGENT'];
  const hasViewableDashboard = (user) => rolesOf(user).some((r) => DASHBOARD_ROLES.includes(r));
  const canViewUserCharts = (user) => canViewCharts && hasViewableDashboard(user);

  const handleViewPerformance = (user) => {
    navigate(`/users/${user.id}/performance`, { state: { user } });
  };

  // Bir admin KENDİ rollerini düzenleyebilir; ama BAŞKA bir admin'in rollerini düzenleyemez.
  const isOtherAdmin = (u) => rolesOf(u).includes('ADMIN') && u.id !== currentUser?.id;

  // -------------------------------------------------------------------------
  // State
  // -------------------------------------------------------------------------
  const [users, setUsers]             = useState([]);
  const [loading, setLoading]         = useState(true);
  // İlk yükleme ile sonraki refetch'leri ayırır: ilk açılışta ortalı spinner, sonraki
  // filtre/sıralama/sayfa değişimlerinde liste ekranda kalır (ListLoadingOverlay).
  const [loadedOnce, setLoadedOnce]   = useState(false);
  const initialLoading = loading && !loadedOnce;

  // Arama + rol filtresi + sayfalama + sıralama URL'de tutulur (F5 / yer imi / link paylaşımı korur).
  const { str, num, arr, setParams, searchParams } = useUrlState();
  const search     = str('search');
  const roleFilter = arr('role');
  const page       = num('page', 0);
  const size       = num('size', 20);
  const sortBy     = str('sortBy', 'name');
  const sortDir    = str('sortDir', 'asc');
  const setSearch     = (v) => setParams({ search: v });
  const setRoleFilter = (v) => setParams({ role: v });
  const setPage       = (v) => setParams({ page: v ? v : '' }, { resetPage: false });
  const setSize       = (v) => setParams({ size: v === 20 ? '' : v });

  const [totalPages, setTotalPages]   = useState(0);
  const [totalItems, setTotalItems]   = useState(0);

  const [isModalOpen, setIsModalOpen] = useState(false);

  // Edit Role modal state
  const [editRoleUser, setEditRoleUser]               = useState(null);
  const [isEditRoleModalOpen, setIsEditRoleModalOpen] = useState(false);

  // Status toggle state
  const [statusLoadingId, setStatusLoadingId] = useState(null);

  // Sütun başlığına tıklanınca sıralamayı çevirir (bilet tablolarındaki toggleSort ile aynı).
  const toggleSort = (field) => {
    const nextDir = sortBy === field ? (sortDir === 'asc' ? 'desc' : 'asc') : 'asc';
    setParams({ sortBy: field === 'name' ? '' : field, sortDir: nextDir === 'asc' ? '' : nextDir });
  };

  // -------------------------------------------------------------------------
  // Veri çekme
  // -------------------------------------------------------------------------
  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({ page, size, sortBy, sortDir });
      if (search) params.set('search', search);
      roleFilter.forEach((r) => params.append('role', r));

      const res = await api.get(`/users?${params}`);
      setUsers(res.data.content);
      setTotalPages(res.data.totalPages);
      setTotalItems(res.data.totalElements);
    } catch {
      toast.error(t('userManagement.errorLoad'));
    } finally {
      setLoading(false);
      setLoadedOnce(true);
    }
    // searchParams tüm filtre/sayfa/sıralama paramlarını kapsar — kimliği yalnızca URL değişince değişir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, t]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------
  const handleUserCreated = (_newUser) => {
    toast.success(t('userManagement.success'));
    // Listeyi yenile — yeni kullanıcı ilk sayfada görünsün
    setPage(0);
    fetchUsers();
  };

  const handleOpenEditRole = (user) => {
    setEditRoleUser(user);
    setIsEditRoleModalOpen(true);
  };

  const handleRoleUpdated = (updatedUser) => {
    toast.success(t('userManagement.editRole.successMsg'));
    setUsers((prev) =>
      prev.map((u) => (u.id === updatedUser.id ? { ...u, role: updatedUser.role, roles: updatedUser.roles } : u))
    );
  };

  const handleToggleStatus = async (user) => {
    const newActive = !user.isActive;
    const confirmKey = newActive
      ? 'userManagement.status.confirmActivate'
      : 'userManagement.status.confirmDeactivate';
    if (!window.confirm(t(confirmKey, { name: user.fullName }))) return;

    setStatusLoadingId(user.id);
    try {
      const res = await updateUserStatus(user.id, newActive);
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, isActive: res.data.isActive } : u))
      );
      toast.success(t(newActive
        ? 'userManagement.status.successActivate'
        : 'userManagement.status.successDeactivate'));
    } catch (err) {
      const msg = err.response?.data?.message;
      toast.error(msg || t('userManagement.status.error'));
    } finally {
      setStatusLoadingId(null);
    }
  };

  const handleClearFilters = () => setParams({ search: '', role: [] });

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

      {/* Edit Role Modal */}
      <EditRoleModal
        isOpen={isEditRoleModalOpen}
        onClose={() => { setIsEditRoleModalOpen(false); setEditRoleUser(null); }}
        user={editRoleUser}
        onRoleUpdated={handleRoleUpdated}
      />

      {/* Sayfa başlığı */}
      <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
            {t('userManagement.title')}
          </h1>
          <p className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {t('userManagement.subtitle')}
          </p>
        </div>

        {canManageUsers && (
          <button
            onClick={() => setIsModalOpen(true)}
            data-tour="users-create"
            className="inline-flex w-full items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors cursor-pointer sm:w-auto"
          >
            <UserPlus className="h-4 w-4" />
            {t('userManagement.createUser')}
          </button>
        )}
      </div>

      {/* Tablo kartı */}
      <div
        data-tour="users-table"
        className="rounded-xl border overflow-hidden"
        style={{
          backgroundColor: 'var(--bg-surface)',
          borderColor: 'var(--border-color)',
          boxShadow: 'var(--shadow-sm)',
        }}
      >
        {/* Header + Filtreler */}
        <div
          className="px-4 py-4 border-b flex flex-col sm:flex-row sm:flex-wrap sm:items-center sm:justify-between gap-3 sm:px-6"
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

          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
            <FilterSearchInput
              value={search}
              onChange={setSearch}
              placeholder={t('userManagement.table.searchPlaceholder')}
              width="13rem"
              debounceMs={300}
            />
            <MultiSelectFilter
              values={roleFilter}
              onChange={setRoleFilter}
              placeholder={t('admin.panel.allRoles')}
              options={ROLES.map((r) => ({ value: r, label: r }))}
            />
            {(search || roleFilter.length > 0) && (
              <ClearFiltersButton onClick={handleClearFilters} />
            )}
          </div>
        </div>

        {/* Tablo */}
        <ListLoadingOverlay initial={initialLoading} loading={loading}>
            <ul className="lg:hidden space-y-3 p-4">
              {users.map((user) => {
                const isInactive = user.isActive === false;
                const isStatusLoading = statusLoadingId === user.id;
                return (
                  <li
                    key={user.id}
                    className="rounded-xl border p-4"
                    style={{
                      backgroundColor: 'var(--bg-surface)',
                      borderColor: 'var(--border-color)',
                      opacity: isInactive ? 0.6 : 1,
                    }}
                  >
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="text-sm font-semibold break-words" style={{ color: 'var(--text-primary)' }}>
                        {user.fullName}
                        {isInactive && (
                          <span className="ml-2 text-[10px] font-normal" style={{ color: 'var(--text-tertiary)' }}>
                            ({t('userManagement.status.inactive')})
                          </span>
                        )}
                      </span>
                      {rolesOf(user).length ? (
                        <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
                          {rolesOf(user).map((r) => (
                            <span
                              key={r}
                              className="inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold"
                              style={roleBadgeStyle(r)}
                            >
                              {r}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span
                          className="inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-[10px] font-medium"
                          style={{ backgroundColor: 'rgba(100,116,139,0.1)', color: 'var(--text-tertiary)', border: '1px dashed var(--border-color)' }}
                        >
                          {t('userManagement.table.noRole')}
                        </span>
                      )}
                    </div>
                    <dl className="text-xs space-y-1" style={{ color: 'var(--text-secondary)' }}>
                      <div className="flex justify-between gap-2">
                        <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                          {t('userManagement.table.email')}
                        </dt>
                        <dd className="text-right break-all">{user.email}</dd>
                      </div>
                      <div className="flex justify-between gap-2">
                        <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                          {t('userManagement.table.username')}
                        </dt>
                        <dd className="text-right font-mono break-all" title={user.id}>
                          {user.id ? user.id.split('-')[0] + '…' : '—'}
                        </dd>
                      </div>
                      <div className="flex justify-between gap-2">
                        <dt className="text-[11px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>
                          {t('userManagement.table.createdAt')}
                        </dt>
                        <dd className="text-right">{formatDate(user.createdAt)}</dd>
                      </div>
                    </dl>
                    {(canViewUserCharts(user) || canManageUsers) && (
                      <div className="mt-3 pt-3 border-t flex flex-col gap-2" style={{ borderColor: 'var(--border-color-light)' }}>
                        {canViewUserCharts(user) && (
                          <button
                            onClick={() => handleViewPerformance(user)}
                            className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
                            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                          >
                            <BarChart3 className="h-3.5 w-3.5" />
                            {t('userPerformance.viewAction')}
                          </button>
                        )}
                        {canManageUsers && (
                        <button
                          onClick={() => handleOpenEditRole(user)}
                          disabled={isInactive || isOtherAdmin(user)}
                          title={isOtherAdmin(user) ? t('userManagement.editRole.adminProtected') : undefined}
                          className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10 disabled:opacity-40 disabled:cursor-not-allowed"
                          style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                        >
                          <ShieldCheck className="h-3.5 w-3.5" />
                          {t('userManagement.editRole.buttonTitle')}
                        </button>
                        )}
                        {canManageUsers && (
                        <button
                          onClick={() => handleToggleStatus(user)}
                          disabled={isStatusLoading}
                          className="inline-flex w-full items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                          style={{
                            borderColor: isInactive ? 'rgba(34,197,94,0.4)' : 'rgba(239,68,68,0.4)',
                            color: isInactive ? '#16a34a' : '#dc2626',
                            backgroundColor: isInactive ? 'rgba(34,197,94,0.06)' : 'rgba(239,68,68,0.06)',
                          }}
                        >
                          {isStatusLoading ? (
                            <span className="h-3.5 w-3.5 rounded-full border-2 animate-spin inline-block"
                              style={{ borderColor: 'currentColor', borderTopColor: 'transparent' }} />
                          ) : isInactive ? (
                            <UserCheck className="h-3.5 w-3.5" />
                          ) : (
                            <UserX className="h-3.5 w-3.5" />
                          )}
                          {t(isInactive
                            ? 'userManagement.status.activateTitle'
                            : 'userManagement.status.deactivateTitle')}
                        </button>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
              {users.length === 0 && (
                <li
                  className="rounded-xl border text-center py-12 text-sm"
                  style={{ borderColor: 'var(--border-color)', color: 'var(--text-tertiary)' }}
                >
                  {search || roleFilter.length > 0
                    ? t('admin.panel.noUsersFiltered')
                    : t('userManagement.table.noUsers')}
                </li>
              )}
            </ul>
            <div className="hidden lg:block overflow-x-auto">
              <table className="w-full resizable-table" style={{ tableLayout: 'fixed', minWidth: `${tableWidth}px` }}>
              {renderColgroup()}
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-surface-secondary)' }}>
                  <SortableTh field="name"      label={t('userManagement.table.name')}      sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('name')} />
                  <SortableTh field="email"     label={t('userManagement.table.email')}     sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('email')} />
                  <SortableTh field="role"      label={t('userManagement.table.role')}      sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('role')} />
                  <SortableTh field="username"  label={t('userManagement.table.username')} resizeHandle={handleFor('username')} />
                  <SortableTh field="createdAt" label={t('userManagement.table.createdAt')} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} resizeHandle={handleFor('createdAt')} />
                  <SortableTh field="actions"   label={t('userManagement.table.actions')} align="right" />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const isInactive = user.isActive === false;
                  const isStatusLoading = statusLoadingId === user.id;
                  return (
                  <tr
                    key={user.id}
                    style={{ opacity: isInactive ? 0.5 : 1 }}
                  >
                    {/* Ad Soyad */}
                    <td
                      className="px-4 py-3 text-sm font-semibold truncate"
                      style={{ color: 'var(--text-primary)' }}
                    >
                      {user.fullName}
                      {isInactive && (
                        <span className="ml-2 text-[10px] font-normal" style={{ color: 'var(--text-tertiary)' }}>
                          ({t('userManagement.status.inactive')})
                        </span>
                      )}
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
                          {t('userManagement.table.noRole')}
                        </span>
                      )}
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

                    {/* İşlemler — chart görüntüleme ADMIN+MANAGER (yalnızca dashboard'u olan roller), yazma yalnızca ADMIN */}
                    <td className="px-4 py-3 text-right">
                      {(canViewUserCharts(user) || canManageUsers) ? (
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Performans chart'ları */}
                          {canViewUserCharts(user) && (
                            <button
                              onClick={() => handleViewPerformance(user)}
                              className="inline-flex items-center gap-1 rounded-lg border px-2 py-1 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10"
                              style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                              title={t('userPerformance.viewAction')}
                            >
                              <BarChart3 className="h-3.5 w-3.5" />
                            </button>
                          )}
                          {/* Rol düzenle */}
                          {canManageUsers && (
                          <button
                            onClick={() => handleOpenEditRole(user)}
                            disabled={isInactive || isOtherAdmin(user)}
                            className="inline-flex items-center gap-1 rounded-lg border px-2 py-1 text-xs font-semibold transition-colors cursor-pointer hover:bg-primary-50 dark:hover:bg-primary-500/10 disabled:opacity-40 disabled:cursor-not-allowed"
                            style={{ borderColor: 'var(--border-color)', color: 'var(--text-secondary)' }}
                            title={isOtherAdmin(user) ? t('userManagement.editRole.adminProtected') : t('userManagement.editRole.buttonTitle')}
                          >
                            <ShieldCheck className="h-3.5 w-3.5" />
                          </button>
                          )}

                          {/* Aktif/Pasif toggle */}
                          {canManageUsers && (
                          <button
                            onClick={() => handleToggleStatus(user)}
                            disabled={isStatusLoading}
                            className="inline-flex items-center gap-1 rounded-lg border px-2 py-1 text-xs font-semibold transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                            style={{
                              borderColor: isInactive ? 'rgba(34,197,94,0.4)' : 'rgba(239,68,68,0.4)',
                              color: isInactive ? '#16a34a' : '#dc2626',
                              backgroundColor: isInactive ? 'rgba(34,197,94,0.06)' : 'rgba(239,68,68,0.06)',
                            }}
                            title={t(isInactive
                              ? 'userManagement.status.activateTitle'
                              : 'userManagement.status.deactivateTitle')}
                          >
                            {isStatusLoading ? (
                              <span className="h-3.5 w-3.5 rounded-full border-2 animate-spin inline-block"
                                style={{ borderColor: 'currentColor', borderTopColor: 'transparent' }} />
                            ) : isInactive ? (
                              <UserCheck className="h-3.5 w-3.5" />
                            ) : (
                              <UserX className="h-3.5 w-3.5" />
                            )}
                          </button>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>—</span>
                      )}
                    </td>
                  </tr>
                  );
                })}

                {users.length === 0 && (
                  <tr>
                    <td
                      colSpan="6"
                      className="text-center py-12 text-sm"
                      style={{ color: 'var(--text-tertiary)' }}
                    >
                      {search || roleFilter.length > 0
                        ? t('admin.panel.noUsersFiltered')
                        : t('userManagement.table.noUsers')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            </div>
        </ListLoadingOverlay>

        {/* Sayfalama */}
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
