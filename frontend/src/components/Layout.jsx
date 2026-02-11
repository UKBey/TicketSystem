import React from "react";
import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import keycloak from "../keycloak";

const Layout = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const isManager = keycloak.hasRealmRole("MANAGER");
  const isAgent = keycloak.hasRealmRole("SUPPORT");
  const isCustomer = !isManager && !isAgent;

  let menuItems = [];

  if (isManager) {
    // Manager Menüsü
    menuItems = [
      { name: "Reports & Analytics", path: "/manager/reports", icon: "📊" },
      { name: "Team Performance", path: "/manager/performance", icon: "📈" } // Örnek ekleme
    ];
  } else if (isAgent) {
    // Support (Agent) Menüsü
    menuItems = [
      { name: "Workspace", path: "/agent/workspace", icon: "💻" },
      { name: "Pool", path: "/agent/pool", icon: "📥" },
      { name: "History", path: "/agent/history", icon: "🕒" }
    ];
  } else {
    // Customer (Müşteri) Menüsü
    menuItems = [
      { name: "My Tickets", path: "/customer/tickets", icon: "🎫" },
      { name: "Create Ticket", path: "/customer/create", icon: "➕" }
    ];
  }

  return (
    // --- ANA KAPLAYICI (Flexbox) ---
    // Ekranı dikeyde ikiye böler: Sol (Sidebar) ve Sağ (İçerik)
    <div style={{ display: "flex", minHeight: "100vh", fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif" }}>

      {/* ================= SIDEBAR (SOL MENÜ) ================= */}
      <aside style={{
        width: "260px",
        background: "#1a233a", // Koyu Lacivert Arkaplan
        color: "white",
        display: "flex",
        flexDirection: "column",
        padding: "20px",
        boxShadow: "2px 0 5px rgba(0,0,0,0.1)" // Hafif gölge
      }}>

        {/* 1. LOGO ALANI */}
        <div style={{ marginBottom: "35px", fontSize: "20px", fontWeight: "bold", display: "flex", alignItems: "center", gap: "10px" }}>
          <span style={{ background: "#007bff", padding: "4px 10px", borderRadius: "6px", fontSize: "16px" }}>TS</span>
          IT Service Desk
        </div>

        {/* 2. KULLANICI KARTI (PROFİL GİRİŞİ BURADA) */}
        {/* Bu kutuya tıklayınca '/profile' sayfasına gidecek */}
        <div
          onClick={() => navigate("/profile")}
          style={{
            marginBottom: "30px",
            padding: "15px",
            background: "#2c3e50",
            borderRadius: "8px",
            cursor: "pointer", // Tıklanabilir el işareti
            transition: "0.2s"
          }}
          onMouseEnter={(e) => e.currentTarget.style.background = "#34495e"} // Hover efekti
          onMouseLeave={(e) => e.currentTarget.style.background = "#2c3e50"}
        >
          <div style={{ fontSize: "11px", color: "#8b9bb4", textTransform: "uppercase", letterSpacing: "1px" }}>Logged in as</div>
          <div style={{ fontWeight: "bold", marginTop: "5px", fontSize: "15px" }}>
            {keycloak.tokenParsed?.preferred_username || "Kullanıcı"}
          </div>
          <div style={{ fontSize: "12px", color: "#007bff", marginTop: "4px", fontWeight: "500" }}>
            {isManager ? "Manager" : isAgent ? "Support Agent" : "Customer"}
          </div>
          <div style={{ fontSize: "10px", color: "#aaa", marginTop: "8px", display: "flex", alignItems: "center", gap: "5px" }}>
            ⚙️ Profili Düzenle
          </div>
        </div>

        {/* 3. MENÜ LİNKLERİ */}
        <nav style={{ flex: 1 }}>
          <ul style={{ listStyle: "none", padding: 0 }}>
            {menuItems.map((item) => (
              <li key={item.path} style={{ marginBottom: "8px" }}>
                <Link
                  to={item.path}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "12px",
                    padding: "12px 15px",
                    // Eğer şu anki sayfa bu link ise mavi yap, değilse şeffaf
                    color: location.pathname === item.path ? "white" : "#aab5c5",
                    background: location.pathname === item.path ? "#007bff" : "transparent",
                    textDecoration: "none",
                    borderRadius: "8px",
                    fontWeight: location.pathname === item.path ? "600" : "normal",
                    transition: "all 0.3s ease"
                  }}
                >
                  <span style={{ fontSize: "18px" }}>{item.icon}</span>
                  {item.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        {/* 4. ÇIKIŞ YAP BUTONU */}
        <button
          onClick={() => keycloak.logout()}
          style={{
            background: "transparent",
            border: "1px solid #34405a",
            color: "#8b9bb4",
            padding: "12px",
            borderRadius: "8px",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "10px",
            marginTop: "auto", // En alta iter
            transition: "0.3s"
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.borderColor = "#ff4d4d";
            e.currentTarget.style.color = "#ff4d4d";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.borderColor = "#34405a";
            e.currentTarget.style.color = "#8b9bb4";
          }}
        >
          🚪 Güvenli Çıkış
        </button>
      </aside>

      {/* ================= MAIN CONTENT (SAĞ İÇERİK) ================= */}
      <main style={{ flex: 1, background: "#f5f7fb", padding: "40px", overflowY: "auto" }}>
        {/* <Outlet /> React Router'ın sihirli bileşenidir.
            URL değiştiğinde CustomerDashboard, ManagerReports gibi sayfalar tam buraya yüklenir. */}
        <Outlet />
      </main>

    </div>
  );
};

export default Layout;