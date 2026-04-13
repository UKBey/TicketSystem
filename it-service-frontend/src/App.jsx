import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Sidebar from './components/Sidebar';
import ProtectedRoute from './components/ProtectedRoute';

// Pages
import LoginPage from './pages/LoginPage';
import MyTickets from './pages/customer/MyTickets';
import Pool from './pages/agent/Pool';
import Workspace from './pages/agent/Workspace';
import History from './pages/agent/History';
import Dashboard from './pages/manager/Dashboard';
import AdminPanel from './pages/manager/AdminPanel';
import ProductPanel from './pages/manager/ProductPanel';
import TicketDetail from './pages/TicketDetail';
import ProfilePage from './pages/ProfilePage';

function AppLayout({ children }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className={`app-layout ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((prev) => !prev)}
      />
      <main className="main-content">{children}</main>
    </div>
  );
}

function HomeRedirect() {
  const { getPrimaryRole } = useAuth();
  const role = getPrimaryRole();

  switch (role) {
    case 'MANAGER':
      return <Navigate to="/workspace" replace />;
    case 'AGENT':
      return <Navigate to="/workspace" replace />;
    case 'CUSTOMER':
      return <Navigate to="/my-tickets" replace />;
    default:
      return <Navigate to="/" replace />;
  }
}

export default function App() {
  const { authenticated } = useAuth();

  return (
    <BrowserRouter>
      <Routes>
        {/* Public */}
        <Route
          path="/"
          element={authenticated ? <HomeRedirect /> : <LoginPage />}
        />

        {/* Customer */}
        <Route
          path="/my-tickets"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <AppLayout><MyTickets /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Agent + Manager */}
        <Route
          path="/workspace"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'MANAGER']}>
              <AppLayout><Workspace /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/pool"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'MANAGER']}>
              <AppLayout><Pool /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/history"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'MANAGER']}>
              <AppLayout><History /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Manager Only */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <AppLayout><Dashboard /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <AppLayout><AdminPanel /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/products"
          element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <AppLayout><ProductPanel /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Ticket Detail (All authenticated users) */}
        <Route
          path="/tickets/:id"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'MANAGER']}>
              <AppLayout><TicketDetail /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Profile (All authenticated users) */}
        <Route
          path="/profile"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'MANAGER']}>
              <AppLayout><ProfilePage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
