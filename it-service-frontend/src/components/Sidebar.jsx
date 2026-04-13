import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Sidebar() {
  const { user, getPrimaryRole, logout } = useAuth();
  const primaryRole = getPrimaryRole();

  return (
    <aside className="sidebar">
      {/* Brand */}
      <div className="sidebar-brand">
        <h2>
          <span className="sidebar-brand-icon">🎫</span>
          IT Service Desk
        </h2>
      </div>

      {/* User Info */}
      <div className="sidebar-user">
        <div>Logged in as</div>
        <div className="sidebar-user-name">{user?.name || 'User'}</div>
        <div>{primaryRole}</div>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        {/* CUSTOMER */}
        {primaryRole === 'CUSTOMER' && (
          <NavLink to="/my-tickets" className={({ isActive }) => isActive ? 'active' : ''}>
            <span className="sidebar-nav-icon">📋</span>
            My Tickets
          </NavLink>
        )}

        {/* AGENT */}
        {(primaryRole === 'AGENT' || primaryRole === 'MANAGER') && (
          <>
            <NavLink to="/workspace" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">💼</span>
              Workspace
            </NavLink>
            <NavLink to="/pool" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📥</span>
              Pool
            </NavLink>
            <NavLink to="/history" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📜</span>
              History
            </NavLink>
          </>
        )}

        {/* MANAGER */}
        {primaryRole === 'MANAGER' && (
          <>
            <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📊</span>
              Dashboard
            </NavLink>
            <NavLink to="/admin" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">⚙️</span>
              Admin Panel
            </NavLink>
            <NavLink to="/products" className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="sidebar-nav-icon">📦</span>
              Product Panel
            </NavLink>
          </>
        )}
      </nav>

      {/* Logout */}
      <div className="sidebar-footer">
        <button className="sidebar-logout" onClick={logout}>
          <span>↩</span> Logout
        </button>
      </div>
    </aside>
  );
}
