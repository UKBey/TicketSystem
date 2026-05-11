import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from 'react-i18next';
import {
  TicketCheck,
  Briefcase,
  Inbox,
  History,
  Users,
  UserPlus,
  LayoutDashboard,
  Settings,
  Package,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Headset,
} from 'lucide-react';

const navLinkBase =
  'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200';

export default function Sidebar({ collapsed = false, onToggle }) {
  const { getPrimaryRole, logout } = useAuth();
  const { t } = useTranslation();
  const primaryRole = getPrimaryRole();

  const linkClassName = ({ isActive }) => {
    const active = isActive
      ? 'text-white bg-white/[0.12]'
      : 'text-slate-400 hover:text-white hover:bg-white/[0.07]';
    return `${navLinkBase} ${active} ${collapsed ? 'justify-center px-2' : ''}`;
  };

  return (
    <aside
      className="fixed left-0 top-0 bottom-0 z-40 flex flex-col border-r transition-all duration-300"
      style={{
        width: collapsed ? '76px' : '260px',
        backgroundColor: 'var(--bg-sidebar)',
        borderColor: 'rgba(255,255,255,0.06)',
      }}
    >
      {/* Brand */}
      <div className="flex h-16 items-center px-4 border-b" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        {collapsed ? (
          /* Collapsed: logo centered, toggle button below in nav area */
          <div className="flex w-full items-center justify-center">
            <button
              onClick={onToggle}
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-500 text-white cursor-pointer hover:bg-primary-600 transition-colors"
              aria-label={t('sidebar.expand')}
              title={t('sidebar.expand')}
            >
              <Headset className="h-5 w-5" />
            </button>
          </div>
        ) : (
          /* Expanded: logo + title + collapse button */
          <>
            <div className="flex items-center gap-2.5 flex-1 min-w-0">
              <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-500 text-white">
                <Headset className="h-5 w-5" />
              </div>
              <span className="text-[15px] font-bold text-white whitespace-nowrap truncate">
                IT Service Desk
              </span>
            </div>
            <button
              onClick={onToggle}
              className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white cursor-pointer ml-2"
              aria-label={t('sidebar.collapse')}
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          </>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {/* Customer section */}
        {primaryRole === 'CUSTOMER' && (
          <NavLink to="/my-tickets" className={linkClassName}>
            <TicketCheck className="h-[18px] w-[18px] flex-shrink-0" />
            {!collapsed && <span>{t('sidebar.myTickets')}</span>}
          </NavLink>
        )}

        {/* Agent/Agent_Admin shared section */}
        {(primaryRole === 'AGENT' || primaryRole === 'AGENT_ADMIN') && (
          <>
            <NavLink to="/workspace" className={linkClassName}>
              <Briefcase className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.workspace')}</span>}
            </NavLink>
            <NavLink to="/pool" className={linkClassName}>
              <Inbox className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.pool')}</span>}
            </NavLink>
            <NavLink to="/history" className={linkClassName}>
              <History className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.history')}</span>}
            </NavLink>
            <NavLink to="/team" className={linkClassName}>
              <Users className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.teamTickets')}</span>}
            </NavLink>
          </>
        )}

        {/* Agent_Admin-only management section */}
        {primaryRole === 'AGENT_ADMIN' && (
          <>
            {!collapsed && (
              <div className="px-3 pt-5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                {t('sidebar.management')}
              </div>
            )}
            <NavLink to="/admin" className={linkClassName}>
              <Settings className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.admin')}</span>}
            </NavLink>
            <NavLink to="/user-management" className={linkClassName}>
              <UserPlus className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.userManagement')}</span>}
            </NavLink>
            <NavLink to="/products" className={linkClassName}>
              <Package className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.products')}</span>}
            </NavLink>
          </>
        )}

        {/* Manager-only dashboard section (read-only) */}
        {primaryRole === 'MANAGER' && (
          <>
            {!collapsed && (
              <div className="px-3 pt-5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                {t('sidebar.analytics')}
              </div>
            )}
            <NavLink to="/dashboard" className={linkClassName}>
              <LayoutDashboard className="h-[18px] w-[18px] flex-shrink-0" />
              {!collapsed && <span>{t('sidebar.dashboard')}</span>}
            </NavLink>
          </>
        )}
      </nav>

      {/* Footer */}
      <div className="px-3 py-4 border-t" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        <button
          onClick={logout}
          className={`${navLinkBase} w-full text-slate-400 hover:text-red-400 hover:bg-red-500/10 cursor-pointer ${collapsed ? 'justify-center px-2' : ''}`}
        >
          <LogOut className="h-[18px] w-[18px] flex-shrink-0" />
          {!collapsed && <span>{t('sidebar.logout')}</span>}
        </button>
      </div>
    </aside>
  );
}
