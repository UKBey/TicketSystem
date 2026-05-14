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
  Headset,
  X,
} from 'lucide-react';

const navLinkBase =
  'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200';

export default function Sidebar({ collapsed = false, onToggle, mobileOpen = false, onMobileClose }) {
  const { getPrimaryRole, logout } = useAuth();
  const { t } = useTranslation();
  const primaryRole = getPrimaryRole();

  // Mobilde her navigasyondan sonra drawer'i kapat — masaustunde no-op.
  const handleNavClick = () => {
    if (onMobileClose) onMobileClose();
  };

  const linkClassName = ({ isActive }) => {
    const active = isActive
      ? 'text-white bg-white/[0.12]'
      : 'text-slate-400 hover:text-white hover:bg-white/[0.07]';
    // collapsed sadece masaustu davranisi — md altinda her zaman full-width drawer.
    return `${navLinkBase} ${active} ${collapsed ? 'md:justify-center md:px-2' : ''}`;
  };

  // md (768px) altinda: drawer her zaman 260px ve translate ile gosterilir/saklanir.
  // md ve uzeri: collapsed durumuna gore 76 veya 260 px.
  const desktopWidthClass = collapsed ? 'md:w-[76px]' : 'md:w-[260px]';

  return (
    <>
      {/* Mobile backdrop — md altinda drawer acikken gosterilir */}
      {mobileOpen && (
        <div
          onClick={onMobileClose}
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
          aria-hidden="true"
        />
      )}
      <aside
        className={`fixed left-0 top-0 bottom-0 z-40 flex flex-col border-r w-[260px] transition-all duration-300 md:translate-x-0 ${desktopWidthClass} ${
          mobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
        style={{
          backgroundColor: 'var(--bg-sidebar)',
          borderColor: 'rgba(255,255,255,0.06)',
        }}
      >
      {/* Brand */}
      <div className="flex h-16 items-center px-4 border-b" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        {/* Desktop collapsed durumu — sadece md ve uzeri, sadece logo + expand butonu */}
        {collapsed && (
          <div className="hidden md:flex w-full items-center justify-center">
            <button
              onClick={onToggle}
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-500 text-white cursor-pointer hover:bg-primary-600 transition-colors"
              aria-label={t('sidebar.expand')}
              title={t('sidebar.expand')}
            >
              <Headset className="h-5 w-5" />
            </button>
          </div>
        )}

        {/* Mobil her zaman + masaustu (genis modda) — logo + baslik + sag taraf butonu */}
        <div className={`items-center gap-2.5 flex-1 min-w-0 ${collapsed ? 'flex md:hidden' : 'flex'}`}>
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-500 text-white">
            <Headset className="h-5 w-5" />
          </div>
          <span className="text-[15px] font-bold text-white whitespace-nowrap truncate">
            IT Service Desk
          </span>
        </div>

        {/* Masaustu (genis modda) — collapse butonu */}
        {!collapsed && (
          <button
            onClick={onToggle}
            className="hidden md:flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white cursor-pointer ml-2"
            aria-label={t('sidebar.collapse')}
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
        )}

        {/* Mobil — drawer'i kapatan X butonu */}
        <button
          onClick={onMobileClose}
          className="md:hidden flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white cursor-pointer ml-2"
          aria-label={t('sidebar.collapse')}
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      {/* Navigation — label'lar collapsed durumunda md+ icin gizlenir, mobilde her zaman gorulur */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {/* Customer section */}
        {primaryRole === 'CUSTOMER' && (
          <NavLink to="/my-tickets" className={linkClassName} onClick={handleNavClick}>
            <TicketCheck className="h-[18px] w-[18px] flex-shrink-0" />
            <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.myTickets')}</span>
          </NavLink>
        )}

        {/* Agent/Agent_Admin shared section */}
        {(primaryRole === 'AGENT' || primaryRole === 'AGENT_ADMIN') && (
          <>
            <NavLink to="/workspace" className={linkClassName} onClick={handleNavClick}>
              <Briefcase className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.workspace')}</span>
            </NavLink>
            <NavLink to="/pool" className={linkClassName} onClick={handleNavClick}>
              <Inbox className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.pool')}</span>
            </NavLink>
            <NavLink to="/history" className={linkClassName} onClick={handleNavClick}>
              <History className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.history')}</span>
            </NavLink>
            <NavLink to="/team" className={linkClassName} onClick={handleNavClick}>
              <Users className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.teamTickets')}</span>
            </NavLink>
          </>
        )}

        {/* Management bölümü — Agent Admin ve Manager paylaşır. */}
        {(primaryRole === 'AGENT_ADMIN' || primaryRole === 'MANAGER') && (
          <>
            <div className={`px-3 pt-5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500 ${collapsed ? 'md:hidden' : ''}`}>
              {t('sidebar.management')}
            </div>
            {primaryRole === 'MANAGER' && (
              <NavLink to="/dashboard" className={linkClassName} onClick={handleNavClick}>
                <LayoutDashboard className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.dashboard')}</span>
              </NavLink>
            )}
            <NavLink to="/admin" className={linkClassName} onClick={handleNavClick}>
              <Settings className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.admin')}</span>
            </NavLink>
            <NavLink to="/user-management" className={linkClassName} onClick={handleNavClick}>
              <UserPlus className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.userManagement')}</span>
            </NavLink>
            <NavLink to="/products" className={linkClassName} onClick={handleNavClick}>
              <Package className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.products')}</span>
            </NavLink>
          </>
        )}
      </nav>

      {/* Footer */}
      <div className="px-3 py-4 border-t" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        <button
          onClick={logout}
          className={`${navLinkBase} w-full text-slate-400 hover:text-red-400 hover:bg-red-500/10 cursor-pointer ${collapsed ? 'md:justify-center md:px-2' : ''}`}
        >
          <LogOut className="h-[18px] w-[18px] flex-shrink-0" />
          <span className={collapsed ? 'md:hidden' : ''}>{t('sidebar.logout')}</span>
        </button>
      </div>
    </aside>
  );
}
