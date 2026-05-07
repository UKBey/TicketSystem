import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Sidebar from './components/Sidebar';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';

// Uygulama rotalarinda kullanilan sayfa bilesenleri.
import LoginPage from './pages/LoginPage';
import MyTickets from './pages/customer/MyTickets';
import Pool from './pages/agent/Pool';
import Workspace from './pages/agent/Workspace';
import History from './pages/agent/History';
import TeamTickets from './pages/agent/TeamTickets';
import Dashboard from './pages/manager/Dashboard';
import AdminPanel from './pages/manager/AdminPanel';
import ProductPanel from './pages/manager/ProductPanel';
import TicketDetail from './pages/TicketDetail';
import ProductPage from './pages/ProductPage';
import ProfilePage from './pages/ProfilePage';
import NotificationPreferencesPage from './pages/NotificationPreferencesPage';

function AppLayout({ children }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className="flex min-h-screen" style={{ backgroundColor: 'var(--bg-body)' }}>
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((prev) => !prev)}
      />
      <div
        className="flex flex-1 flex-col transition-all duration-300"
        style={{ marginLeft: sidebarCollapsed ? '76px' : '260px' }}
      >
        <Navbar />
        <main className="flex-1 p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}

function HomeRedirect() {
  const { getPrimaryRole } = useAuth();
  const role = getPrimaryRole();

  switch (role) {
    case 'AGENT_ADMIN':
      return <Navigate to="/workspace" replace />;
    case 'MANAGER':
      return <Navigate to="/dashboard" replace />;
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
        {/* Oturum gerektirmeyen giris rotasi. */}
        <Route
          path="/"
          element={authenticated ? <HomeRedirect /> : <LoginPage />}
        />

        {/* Musteri rolune acik alanlar. */}
        <Route
          path="/my-tickets"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <AppLayout><MyTickets /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Agent ve agent_admin tarafindan kullanilan calisma ekranlari. */}
        <Route
          path="/workspace"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'AGENT_ADMIN']}>
              <AppLayout><Workspace /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/pool"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'AGENT_ADMIN']}>
              <AppLayout><Pool /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/history"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'AGENT_ADMIN']}>
              <AppLayout><History /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/team"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'AGENT_ADMIN']}>
              <AppLayout><TeamTickets /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Manager (dashboard-only) ve agent_admin yetkisi isteyen yonetim ekranlari. */}
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
            <ProtectedRoute allowedRoles={['AGENT_ADMIN']}>
              <AppLayout><AdminPanel /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/products"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER']}>
              <AppLayout><ProductPanel /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/products/:id"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER']}>
              <AppLayout><ProductPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Kimligi dogrulanmis tum roller icin bilet detay ekrani. */}
        <Route
          path="/tickets/:id"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER']}>
              <AppLayout><TicketDetail /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Tum rollerin erisebildigi profil sayfasi. */}
        <Route
          path="/profile"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER']}>
              <AppLayout><ProfilePage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Tum rollerin erisebildigi bildirim tercihleri sayfasi. */}
        <Route
          path="/notification-preferences"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER']}>
              <AppLayout><NotificationPreferencesPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Eslesmeyen tum yollari ana rotaya yonlendirir. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
