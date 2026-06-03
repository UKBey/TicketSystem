import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Sidebar from './components/Sidebar';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import RateLimitToast from './components/RateLimitToast';

// Uygulama rotalarinda kullanilan sayfa bilesenleri.
import LoginPage from './pages/LoginPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import MyTickets from './pages/customer/MyTickets';
import Pool from './pages/agent/Pool';
import Workspace from './pages/agent/Workspace';
import History from './pages/agent/History';
import TeamTickets from './pages/agent/TeamTickets';
import AllTickets from './pages/agent/AllTickets';
import Dashboard from './pages/manager/Dashboard';
import AdminPanel from './pages/manager/AdminPanel';
import ProductPanel from './pages/manager/ProductPanel';
import UserManagementPage from './pages/manager/UserManagementPage';
import TicketDetail from './pages/TicketDetail';
import ProductPage from './pages/ProductPage';
import ProfilePage from './pages/ProfilePage';
import NotificationPreferencesPage from './pages/NotificationPreferencesPage';
import KnownIssuesPage from './pages/KnownIssuesPage';
import CannedResponsesPage from './pages/CannedResponsesPage';
import NoRolePage from './pages/NoRolePage';

function AppLayout({ children }) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  // Mobilde sidebar overlay drawer (md altinda ml=0). md ve uzeri icin collapsed durumuna
  // gore 76 / 260 px ml uygulanir. Tailwind dinamik sinif uretmedigi icin iki olasilik
  // sabit class ile kosulluyoruz.
  const desktopMarginClass = sidebarCollapsed ? 'md:ml-[76px]' : 'md:ml-[260px]';

  return (
    <div className="flex min-h-screen" style={{ backgroundColor: 'var(--bg-body)' }}>
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((prev) => !prev)}
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
      />
      {/* min-w-0: flex-1 child'i icindeki overflow-x-auto'lar (TicketTable gibi)
          calissin diye varsayilan min-width:auto'yu kiriyoruz — aksi halde icerik
          wrapper'i kendisi geniyleyip body-level scroll yaratiyor. */}
      <div className={`flex flex-1 flex-col min-w-0 transition-[margin] duration-300 ${desktopMarginClass}`}>
        <Navbar onMenuClick={() => setMobileOpen(true)} />
        <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}

function HomeRedirect() {
  const { getPrimaryRole } = useAuth();
  const role = getPrimaryRole();

  switch (role) {
    case 'ADMIN':
      return <Navigate to="/user-management" replace />;
    case 'MANAGER':
      return <Navigate to="/dashboard" replace />;
    case 'LEAD_AGENT':
    case 'AGENT':
      return <Navigate to="/workspace" replace />;
    case 'CUSTOMER':
      return <Navigate to="/my-tickets" replace />;
    default:
      return <Navigate to="/no-role" replace />;
  }
}

export default function App() {
  const { authenticated } = useAuth();

  return (
    <BrowserRouter>
      <RateLimitToast />
      <Routes>
        {/* Oturum gerektirmeyen giris rotasi. */}
        <Route
          path="/"
          element={authenticated ? <HomeRedirect /> : <LoginPage />}
        />

        {/* Anonim erişim — şifre sıfırlama akışı. Oturum açıksa anasayfaya yönlenir. */}
        <Route
          path="/forgot-password"
          element={authenticated ? <HomeRedirect /> : <ForgotPasswordPage />}
        />
        <Route
          path="/reset-password"
          element={authenticated ? <HomeRedirect /> : <ResetPasswordPage />}
        />

        {/* Yetkili rol atanmamis kullanicilar icin bilgilendirme sayfasi. */}
        <Route
          path="/no-role"
          element={authenticated ? <NoRolePage /> : <Navigate to="/" replace />}
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

        {/* Agent, lead_agent ve admin'in kullandigi calisma ekranlari. */}
        <Route
          path="/workspace"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT']}>
              <AppLayout><Workspace /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/pool"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT']}>
              <AppLayout><Pool /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/history"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT']}>
              <AppLayout><History /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/team"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT']}>
              <AppLayout><TeamTickets /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/all-tickets"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT', 'MANAGER', 'ADMIN']}>
              <AppLayout><AllTickets /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Manager (dashboard-only) ve admin yetkisi isteyen yonetim ekranlari. */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute allowedRoles={['MANAGER', 'LEAD_AGENT', 'ADMIN']}>
              <AppLayout><Dashboard /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AppLayout><AdminPanel /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/user-management"
          element={
            <ProtectedRoute allowedRoles={['ADMIN', 'MANAGER']}>
              <AppLayout><UserManagementPage /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/products"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><ProductPanel /></AppLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/products/:id"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><ProductPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Kimligi dogrulanmis tum roller icin bilet detay ekrani. */}
        <Route
          path="/tickets/:id"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><TicketDetail /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Tum rollerin erisebildigi profil sayfasi. */}
        <Route
          path="/profile"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><ProfilePage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Tum rollerin erisebildigi bildirim tercihleri sayfasi. */}
        <Route
          path="/notification-preferences"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><NotificationPreferencesPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Sıkça karşılaşılan sorunlar — tüm roller erişir, içerik product yetkisine göre filtrelenir. */}
        <Route
          path="/known-issues"
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER']}>
              <AppLayout><KnownIssuesPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Hazır yanıtlar yönetimi — ajanlar/yöneticiler; müşteri erişemez. */}
        <Route
          path="/canned-responses"
          element={
            <ProtectedRoute allowedRoles={['AGENT', 'LEAD_AGENT', 'ADMIN']}>
              <AppLayout><CannedResponsesPage /></AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Eslesmeyen tum yollari ana rotaya yonlendirir. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
