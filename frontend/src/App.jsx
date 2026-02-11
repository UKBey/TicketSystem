import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import Layout from "./components/Layout";
import * as Pages from "./pages/Placeholders";
import keycloak from "./keycloak";

// Yetki Kontrolü Bileşeni (Private Route)
const ProtectedRoute = ({ children, role }) => {
  if (!keycloak.authenticated) {
    return <div>Giriş yapılıyor...</div>;
  }
  // Eğer belirli bir rol isteniyorsa ve kullanıcıda yoksa
  if (role && !keycloak.hasRealmRole(role)) {
     return <div style={{padding: 20, color: 'red'}}>⛔ Yetkisiz Erişim: Bu sayfayı görüntüleme yetkiniz yok.</div>;
  }
  return children;
};

const App = () => {
  // Kullanıcının ana sayfaya (/) girdiğinde nereye gideceğini belirle
  const getDefaultRedirect = () => {
    if (keycloak.hasRealmRole("MANAGER")) return "/manager/reports";
    if (keycloak.hasRealmRole("SUPPORT")) return "/agent/workspace";
    return "/customer/tickets";
  };

  return (
    <Routes>
      {/* Ana Layout İçindeki Rotalar */}
      <Route path="/" element={<Layout />}>

        {/* Varsayılan yönlendirme */}
        <Route index element={<Navigate to={getDefaultRedirect()} replace />} />

        {/* CUSTOMER ROUTES */}
        <Route path="customer/tickets" element={<Pages.CustomerDashboard />} />
        <Route path="customer/create" element={<Pages.CreateTicket />} />

        {/* AGENT ROUTES (Protected: Sadece SUPPORT rolü) */}
        <Route
          path="agent/workspace"
          element={<ProtectedRoute role="SUPPORT"><Pages.AgentWorkspace /></ProtectedRoute>}
        />
        <Route
          path="agent/pool"
          element={<ProtectedRoute role="SUPPORT"><Pages.AgentPool /></ProtectedRoute>}
        />
        <Route
          path="agent/history"
          element={<ProtectedRoute role="SUPPORT"><Pages.AgentHistory /></ProtectedRoute>}
        />

        {/* ADMIN ROUTES (Protected: Sadece ADMIN rolü) */}
        <Route
          path="manager/reports"
          element={<ProtectedRoute role="MANAGER"><Pages.ManagerReports /></ProtectedRoute>}
        />

        <Route path="profile" element={<Pages.Profile />} />

        {/* 404 */}
        <Route path="*" element={<Pages.NotFound />} />
      </Route>
    </Routes>
  );
};

export default App;