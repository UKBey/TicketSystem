/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import keycloak from '../keycloak';
import api from '../services/api';
import i18n from '../i18n';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const initCalled = useRef(false);

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
          api.post('/users/sync').catch(err => console.error('Sync error:', err));
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
      api.post('/users/sync').catch(err => console.error('Sync error:', err));
    };

    keycloak.onAuthLogout = () => {
      setAuthenticated(false);
      setUser(null);
      setRoles([]);
    };
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
