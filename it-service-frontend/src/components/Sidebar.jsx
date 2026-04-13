import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Sidebar({ collapsed = false, onToggle }) {
  const { user, getPrimaryRole, logout } = useAuth();
  const primaryRole = getPrimaryRole();
  const initials = (user?.name || user?.username || 'U')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
      {/* Brand */}
      <div className="sidebar-brand">
        <h2>
          <span className="sidebar-brand-icon">🎫</span>
          <span className="sidebar-label">IT Service Desk</span>
        </h2>
        <button
          className="sidebar-toggle"
          onClick={onToggle}
          aria-label={collapsed ? 'Open sidebar' : 'Close sidebar'}
          title={collapsed ? 'Open sidebar' : 'Close sidebar'}
        >
          {collapsed ? '»' : '«'}
        </button>
      </div>

      <NavLink to="/profile" className={({ isActive }) => `sidebar-profile-link ${isActive ? 'active' : ''}`}>
        <div className="sidebar-profile" title={user?.email || user?.username || ''}>
          <div className="sidebar-profile-avatar">{initials}</div>
          <div className="sidebar-profile-meta sidebar-label">
            <div className="sidebar-profile-name">{user?.name || user?.username || 'User'}</div>
            <div className="sidebar-profile-role">{primaryRole || 'USER'}</div>
          </div>
        </div>
      </NavLink>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {/* CUSTOMER */}
        {primaryRole === 'CUSTOMER' && (
          <NavLink to="/my-tickets" className={({ isActive }) => isActive ? 'active' : ''}>
            <span className="sidebar-nav-icon">📋</span>
            <span className="sidebar-label">My Tickets</span>
          </NavLink>
        )}

        {/* AGENT */}
        {(primaryRole === 'AGENT' || primaryRole === 'MANAGER') && (
          <>
            <NavLink to="/workspace" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">💼</span>
              <span className="sidebar-label">Workspace</span>
            </NavLink>
            <NavLink to="/pool" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📥</span>
              <span className="sidebar-label">Pool</span>
            </NavLink>
            <NavLink to="/history" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📜</span>
              <span className="sidebar-label">History</span>
            </NavLink>
          </>
        )}

        {/* MANAGER */}
        {primaryRole === 'MANAGER' && (
          <>
            <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📊</span>
              <span className="sidebar-label">Dashboard</span>
            </NavLink>
            <NavLink to="/admin" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">⚙️</span>
              <span className="sidebar-label">Admin Panel</span>
            </NavLink>
            <NavLink to="/products" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📦</span>
              <span className="sidebar-label">Product Panel</span>
            </NavLink>
          </>
        )}
      </nav>

      {/* Logout */}
      <div className="sidebar-footer">
        <button className="sidebar-logout" onClick={logout}>
          <span>↩</span>
          <span className="sidebar-label">Logout</span>
        </button>
      </div>
    </aside>
  );
}
