/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import keycloak from '../keycloak';
import api from '../services/api';
import userService from '../services/userService';
import i18n from '../i18n';
import { useTheme } from './ThemeContext';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const initCalled = useRef(false);
  const { theme, applyServerTheme, setTheme } = useTheme();
  // useEffect tek-seferlik calistigi icin theme'i closure'a hapsetmek istemiyoruz —
  // sync sirasinda guncel degeri okuyabilelim diye ref'te tutuyoruz.
  const themeRef = useRef(theme);
  useEffect(() => { themeRef.current = theme; }, [theme]);

  const extractUserInfo = useCallback(() => {
    if (keycloak.tokenParsed) {
      const tokenRoles = keycloak.tokenParsed.realm_access?.roles || [];
      const appRoles = tokenRoles.filter((r) =>
        ['CUSTOMER', 'AGENT', 'AGENT_ADMIN', 'MANAGER'].includes(r)
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
          api.post('/users/sync')
            .then((res) => {
              const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
              api.put('/users/me/language', null, { params: { lang } }).catch(() => {});
              const serverTheme = res?.data?.preferredTheme;
              if (serverTheme === 'light' || serverTheme === 'dark') {
                // Sunucudaki tercih kullanıcının "son seçimi" — UI'ı bununla hizala.
                applyServerTheme(serverTheme);
              } else {
                // Henüz kayıt yoksa localStorage'daki mevcut temayı backend'e yaz.
                setTheme(themeRef.current);
              }
            })
            .catch(err => console.error('Sync error:', err));
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
      api.post('/users/sync')
        .then((res) => {
          const lang = i18n.language?.startsWith('tr') ? 'tr' : 'en';
          api.put('/users/me/language', null, { params: { lang } }).catch(() => {});
          const serverTheme = res?.data?.preferredTheme;
          if (serverTheme === 'light' || serverTheme === 'dark') {
            applyServerTheme(serverTheme);
          } else {
            setTheme(themeRef.current);
          }
        })
        .catch(err => console.error('Sync error:', err));
    };

    keycloak.onAuthLogout = () => {
      setAuthenticated(false);
      setUser(null);
      setRoles([]);
    };

    // Keycloak action callback'i (CONFIGURE_TOTP gibi) — keycloak-js, redirect
    // URL'deki `kc_action_status` query param'ını init() sırasında temizliyor;
    // bu yüzden ProfilePage'in useEffect'i fark edemiyor. Bu callback hook'ı
    // init() URL'i temizlemeden ÖNCE tetikliyor.
    keycloak.onActionUpdate = (status) => {
      if (status === 'success') {
        userService.notifyTotpDeviceAdded().catch((err) => {
          console.warn('2FA add notification failed', err);
        });
      }
    };
  }, [extractUserInfo, applyServerTheme, setTheme]);

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
    const locale = i18n.language?.startsWith('tr') ? 'tr' : 'en';
    keycloak.login({ redirectUri: window.location.origin + '/', locale });
  }, []);

  const logout = useCallback(() => {
    keycloak.logout({ redirectUri: window.location.origin + '/' });
  }, []);

  const hasRole = useCallback(
    (role) => roles.includes(role),
    [roles]
  );

  const getPrimaryRole = useCallback(() => {
    if (roles.includes('AGENT_ADMIN')) return 'AGENT_ADMIN';
    if (roles.includes('MANAGER')) return 'MANAGER';
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
