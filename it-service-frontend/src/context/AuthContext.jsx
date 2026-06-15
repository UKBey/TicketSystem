/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import keycloak, { redirectToKeycloakLogin, redirectToKeycloakLogout } from '../keycloak';
import api from '../services/api';
import i18n from '../i18n';
import { useTheme } from './ThemeContext';
import { useDateFormat } from './DateFormatContext';
import { usePanelPrefs } from './PanelPrefsContext';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const initCalled = useRef(false);
  // init().then ve onAuthSuccess taze login'de IKISI de tetiklenir; tek bir /users/sync
  // yeterli. Ucan istek varsa tekrari engelle — aksi halde yeni kullanicida iki paralel
  // insert PK cakismasiyla 409 Conflict doner.
  const syncInFlight = useRef(false);
  const { theme, setTheme } = useTheme();
  const { applyServerDateFormat } = useDateFormat();
  const { applyServerPanelPrefs } = usePanelPrefs();
  // useEffect tek-seferlik calistigi icin theme'i closure'a hapsetmek istemiyoruz —
  // sync sirasinda guncel degeri okuyabilelim diye ref'te tutuyoruz.
  const themeRef = useRef(theme);
  useEffect(() => { themeRef.current = theme; }, [theme]);

  const extractUserInfo = useCallback(() => {
    if (keycloak.tokenParsed) {
      const tokenRoles = keycloak.tokenParsed.realm_access?.roles || [];
      const appRoles = tokenRoles.filter((r) =>
        ['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER'].includes(r)
      );
      setRoles(appRoles);
      setUser({
        id: keycloak.tokenParsed.sub,
        name: keycloak.tokenParsed.name || keycloak.tokenParsed.preferred_username,
        email: keycloak.tokenParsed.email,
        username: keycloak.tokenParsed.preferred_username,
      });
    }
  }, []);

  useEffect(() => {
    // StrictMode'da useEffect'in cift tetiklenmesine karsi init'i tek sefere sabitler.
    if (initCalled.current) return;
    initCalled.current = true;

    // /users/sync kullaniciyi JWT'den DB'ye senkronlar. Dil ve tema, kullanicinin
    // Keycloak giris ekraninda sectigi degerlerdir (template.ftl bunlari localStorage'a
    // yazar) — uygulama bu degerlerle acilir ve bu secim DB'ye YAZILIR. DB'deki onceki
    // deger client'i ezmez; oncelik her zaman Keycloak ekranindaki secimdedir.
    const syncUserPreferences = () => {
      if (syncInFlight.current) return;
      syncInFlight.current = true;
      api.post('/users/sync')
        .then((res) => {
          const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
          api.put('/users/me/language', null, { params: { lang } }).catch(() => {});
          // setTheme temayi (gorsel degisiklik olmadan) ayarlar ve backend'e persist eder.
          setTheme(themeRef.current);
          // Tarih formatinin Keycloak ekrani kaynagi yok — DB'deki deger client'i besler.
          if (res.data?.preferredDateFormat) applyServerDateFormat(res.data.preferredDateFormat);
          // Panel gorunurluk tercihleri de yalnizca DB'de — client'i besler.
          if (res.data?.panelPreferences) applyServerPanelPrefs(res.data.panelPreferences);
        })
        .catch(err => console.error('Sync error:', err))
        .finally(() => { syncInFlight.current = false; });
    };

    keycloak
      .init({
        onLoad: 'check-sso',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        silentCheckSsoFallback: false,
      })
      .then((auth) => {
        setAuthenticated(auth);
        setInitialized(true);
        if (auth) {
          extractUserInfo();
          syncUserPreferences();
        }
        setLoading(false);
      })
      .catch((err) => {
        console.error('Keycloak init failed', err);
        setInitialized(true);
        setLoading(false);
      });

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => {
        console.warn('Token yenilenemedi, oturum sonlandırılıyor.');
        keycloak.logout();
      });
    };

    keycloak.onAuthSuccess = () => {
      setAuthenticated(true);
      extractUserInfo();
      syncUserPreferences();
    };

    keycloak.onAuthLogout = () => {
      setAuthenticated(false);
      setUser(null);
      setRoles([]);
    };
  }, [extractUserInfo, setTheme, applyServerDateFormat, applyServerPanelPrefs]);

  const refreshUser = useCallback(async () => {
    // Force-refresh token so updated claims (name/email) reach the client,
    // then re-derive the local user state from the new tokenParsed.
    try {
      await keycloak.updateToken(-1);
    } catch (e) {
      console.warn('Token refresh failed during refreshUser', e);
    }
    extractUserInfo();
  }, [extractUserInfo]);

  const login = useCallback(() => {
    redirectToKeycloakLogin({ redirectUri: window.location.origin + '/' });
  }, []);

  const logout = useCallback(() => {
    redirectToKeycloakLogout({ redirectUri: window.location.origin + '/' });
  }, []);

  const hasRole = useCallback(
    (role) => roles.includes(role),
    [roles]
  );

  const hasAnyRole = useCallback(
    (...wanted) => wanted.some((r) => roles.includes(r)),
    [roles]
  );

  // Yetenek yardımcıları (additive çoklu rol — etkin yetki = rollerin birleşimi).
  // LEAD_AGENT, AGENT'ı kapsar (Keycloak composite); etkin yetki rollerin birleşimidir.
  const isAdmin     = roles.includes('ADMIN');
  const isManager   = roles.includes('MANAGER');
  const isLeadAgent = roles.includes('LEAD_AGENT');
  const isAgent     = roles.includes('AGENT') || isLeadAgent;
  const isCustomer  = roles.includes('CUSTOMER');
  const isStaff     = isAgent || isAdmin || isManager;

  // Birincil/landing rolü (yalnızca açılış sayfası için; erişim rollerin birleşimine göre).
  const getPrimaryRole = useCallback(() => {
    if (roles.includes('ADMIN')) return 'ADMIN';
    if (roles.includes('MANAGER')) return 'MANAGER';
    if (roles.includes('LEAD_AGENT')) return 'LEAD_AGENT';
    if (roles.includes('AGENT')) return 'AGENT';
    if (roles.includes('CUSTOMER')) return 'CUSTOMER';
    return null;
  }, [roles]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-4" style={{ backgroundColor: 'var(--bg-body)', color: 'var(--text-secondary)' }}>
        <div className="h-10 w-10 rounded-full border-[3px] animate-spin" style={{ borderColor: 'var(--border-color)', borderTopColor: '#3b82f6' }} />
        <p className="text-sm font-medium">Loading...</p>
      </div>
    );
  }

  return (
    <AuthContext.Provider
      value={{
        initialized,
        authenticated,
        user,
        roles,
        login,
        logout,
        hasRole,
        hasAnyRole,
        isAdmin,
        isManager,
        isLeadAgent,
        isAgent,
        isCustomer,
        isStaff,
        getPrimaryRole,
        refreshUser,
        keycloak,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
