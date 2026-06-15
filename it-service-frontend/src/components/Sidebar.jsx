import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { usePanelPrefs } from '../context/PanelPrefsContext';
import { useTranslation } from 'react-i18next';
import {
  TicketCheck,
  Briefcase,
  Inbox,
  History,
  Users,
  UserPlus,
  LayoutDashboard,
  Gauge,
  Settings,
  Package,
  LifeBuoy,
  LogOut,
  ChevronLeft,
  Headset,
  Layers,
  Zap,
  X,
} from 'lucide-react';

const navLinkBase =
  'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200';

export default function Sidebar({ collapsed = false, onToggle, mobileOpen = false, onMobileClose }) {
  const { logout, isCustomer, isAgent, isLeadAgent, isAdmin, isManager } = useAuth();
  const { isPanelVisible } = usePanelPrefs();
  const { t } = useTranslation();

  // Navigasyon = kullanıcının rollerinin verdiği yetkilerin BİRLEŞİMİ.
  // (lead_agent + admin olan biri hem operasyonel hem yönetim bölümlerini görür.)
  const showOperational = isAgent;                       // workspace/pool/history/team
  const showDashboard   = isManager || isLeadAgent || isAdmin;
  const showManagement  = showDashboard || isAdmin;      // yönetim başlığı
  const showAdmin       = isAdmin;                       // admin ayarları
  const showUserMgmt    = isAdmin || isManager;          // kullanıcı yönetimi (manager salt-okuma)
  const showProducts    = isAdmin || isManager;          // ürün yönetimi
  const showCanned      = isAgent || isAdmin;            // hazır yanıtlar (müşteri görmez)
  const isStaff         = isAgent || isAdmin || isManager;

  // Mobilde her navigasyondan sonra drawer'i kapat — masaustunde no-op.
  const handleNavClick = () => {
    if (onMobileClose) onMobileClose();
  };

  const linkClassName = ({ isActive }) => {
    const active = isActive
      ? 'text-white bg-white/[0.12]'
      : 'text-slate-400 hover:text-white hover:bg-white/[0.07]';
    // Ikon her zaman sola hizali (px-3) ve sabit kalir; collapse'ta yalnizca rail
    // daralip label'i kirpar — boylece ikon zıplamadan/kaymadan ayni yerde durur.
    return `${navLinkBase} ${active}`;
  };

  // md (768px) altinda: drawer her zaman 260px ve translate ile gosterilir/saklanir.
  // md ve uzeri: collapsed durumuna gore 76 veya 260 px.
  const desktopWidthClass = collapsed ? 'md:w-[76px]' : 'md:w-[260px]';

  // Label'lar tek satirda kalir ve collapse'ta opacity ile TAMAMEN kaybolur
  // (yarim/kirpik yazi gozukmesin) — sadece ikonlar kalir. Mobilde her zaman gorunur.
  const labelClass = `whitespace-nowrap transition-opacity duration-200 ${
    collapsed ? 'md:opacity-0' : 'opacity-100'
  }`;

  return (
    <>
      {/* Mobile backdrop — md altinda drawer acikken yumusak fade ile gosterilir */}
      <div
        onClick={onMobileClose}
        className={`fixed inset-0 z-30 bg-black/50 transition-opacity duration-300 md:hidden ${
          mobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        aria-hidden="true"
      />
      <aside
        className={`fixed left-0 top-0 bottom-0 z-40 flex flex-col overflow-hidden border-r w-[260px] transition-[width,transform] duration-300 ease-in-out md:translate-x-0 ${desktopWidthClass} ${
          mobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
        style={{
          backgroundColor: 'var(--bg-sidebar)',
          borderColor: 'rgba(255,255,255,0.06)',
        }}
      >
      {/* Brand — logo sabit kalir (collapse'ta expand butonu olarak calisir),
          baslik ve chevron daralan rail tarafindan kirpilarak silinir. */}
      <div className="flex h-16 items-center gap-2.5 px-4 border-b" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        {/* Logo — masaustunde collapse/expand toggle; mobilde dekoratif (no-op). */}
        <button
          onClick={onToggle}
          className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-500 text-white cursor-pointer transition-colors hover:bg-primary-600"
          aria-label={collapsed ? t('sidebar.expand') : t('sidebar.collapse')}
          title={collapsed ? t('sidebar.expand') : t('sidebar.collapse')}
        >
          <Headset className="h-5 w-5" />
        </button>

        <span className={`flex-1 min-w-0 text-[15px] font-bold text-white whitespace-nowrap transition-opacity duration-200 ${collapsed ? 'md:opacity-0' : 'opacity-100'}`}>
          IT Service Desk
        </span>

        {/* Masaustu — collapse butonu (rail daralinca dogal olarak kirpilir) */}
        <button
          onClick={onToggle}
          className="hidden md:flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white cursor-pointer"
          aria-label={t('sidebar.collapse')}
        >
          <ChevronLeft className="h-4 w-4" />
        </button>

        {/* Mobil — drawer'i kapatan X butonu */}
        <button
          onClick={onMobileClose}
          className="md:hidden flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white cursor-pointer"
          aria-label={t('sidebar.collapse')}
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      {/* Navigation — label'lar collapsed durumunda daralan rail tarafindan kirpilir.
          overflow-x-hidden: dar rail'de yatay scrollbar/tasma olusmasini engeller. */}
      <nav className="flex-1 overflow-y-auto overflow-x-hidden px-3 py-4 space-y-1">
        {/* Customer section */}
        {isCustomer && (
          <>
            <NavLink to="/overview" className={linkClassName} onClick={handleNavClick}>
              <Gauge className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={labelClass}>{t('sidebar.overview')}</span>
            </NavLink>
            <NavLink to="/my-tickets" className={linkClassName} onClick={handleNavClick}>
              <TicketCheck className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={labelClass}>{t('sidebar.myTickets')}</span>
            </NavLink>
          </>
        )}

        {/* Operasyonel bölüm — agent + lead agent */}
        {showOperational && (
          <>
            <NavLink to="/my-performance" className={linkClassName} onClick={handleNavClick}>
              <Gauge className="h-[18px] w-[18px] flex-shrink-0" />
              <span className={labelClass}>{t('sidebar.myPerformance')}</span>
            </NavLink>
            {isPanelVisible('workspace') && (
              <NavLink to="/workspace" className={linkClassName} onClick={handleNavClick}>
                <Briefcase className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.workspace')}</span>
              </NavLink>
            )}
            {isPanelVisible('pool') && (
              <NavLink to="/pool" className={linkClassName} onClick={handleNavClick}>
                <Inbox className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.pool')}</span>
              </NavLink>
            )}
            {isPanelVisible('history') && (
              <NavLink to="/history" className={linkClassName} onClick={handleNavClick}>
                <History className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.history')}</span>
              </NavLink>
            )}
            {isPanelVisible('team') && (
              <NavLink to="/team" className={linkClassName} onClick={handleNavClick}>
                <Users className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.teamTickets')}</span>
              </NavLink>
            )}
          </>
        )}

        {/* Tüm biletler — personel (agent/lead) + yönetici/admin.
            Agent/lead panel tercihinde gizleyebilir; yönetici/admin için tercih
            varsayılan görünür olduğundan etkilenmez. */}
        {isStaff && isPanelVisible('allTickets') && (
          <NavLink to="/all-tickets" className={linkClassName} onClick={handleNavClick}>
            <Layers className="h-[18px] w-[18px] flex-shrink-0" />
            <span className={labelClass}>{t('sidebar.allTickets')}</span>
          </NavLink>
        )}

        {/* Yönetim bölümü — dashboard (manager/lead/admin) + admin ayarları. */}
        {showManagement && (
          <>
            {/* Baslik collapse'ta opacity ile silinir ama yuksekligini korur —
                boylece altindaki linkler dikeyde zıplamaz, sadece yatayda daralir. */}
            <div className={`px-3 pt-5 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500 whitespace-nowrap transition-opacity duration-300 ${collapsed ? 'md:opacity-0' : 'opacity-100'}`}>
              {t('sidebar.management')}
            </div>
            {showDashboard && (
              <NavLink to="/dashboard" className={linkClassName} onClick={handleNavClick}>
                <LayoutDashboard className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.dashboard')}</span>
              </NavLink>
            )}
            {showAdmin && (
              <NavLink to="/admin" className={linkClassName} onClick={handleNavClick}>
                <Settings className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.admin')}</span>
              </NavLink>
            )}
            {showUserMgmt && (
              <NavLink to="/user-management" className={linkClassName} onClick={handleNavClick}>
                <UserPlus className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.userManagement')}</span>
              </NavLink>
            )}
            {showProducts && (
              <NavLink to="/products" className={linkClassName} onClick={handleNavClick}>
                <Package className="h-[18px] w-[18px] flex-shrink-0" />
                <span className={labelClass}>{t('sidebar.products')}</span>
              </NavLink>
            )}
          </>
        )}

        {/* Ajanlar/lead — hazır yanıt yönetimi (müşteri görmez) */}
        {showCanned && (
          <NavLink to="/canned-responses" className={linkClassName} onClick={handleNavClick}>
            <Zap className="h-[18px] w-[18px] flex-shrink-0" />
            <span className={labelClass}>{t('sidebar.cannedResponses')}</span>
          </NavLink>
        )}

        {/* Tum roller — sikca karsilasilan sorunlar bilgi tabani */}
        <NavLink to="/known-issues" className={linkClassName} onClick={handleNavClick}>
          <LifeBuoy className="h-[18px] w-[18px] flex-shrink-0" />
          <span className={labelClass}>{t('sidebar.knownIssues')}</span>
        </NavLink>
      </nav>

      {/* Footer */}
      <div className="px-3 py-4 border-t" style={{ borderColor: 'rgba(255,255,255,0.06)' }}>
        <button
          onClick={logout}
          className={`${navLinkBase} w-full text-slate-400 hover:text-red-400 hover:bg-red-500/10 cursor-pointer`}
        >
          <LogOut className="h-[18px] w-[18px] flex-shrink-0" />
          <span className={labelClass}>{t('sidebar.logout')}</span>
        </button>
      </div>
    </aside>
    </>
  );
}
