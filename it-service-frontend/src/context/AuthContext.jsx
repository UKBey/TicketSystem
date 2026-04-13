import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import keycloak from '../keycloak';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const initCalled = useRef(false);

  useEffect(() => {
    // React 19 StrictMode çift çağrı koruması
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
    };

    keycloak.onAuthLogout = () => {
      setAuthenticated(false);
      setUser(null);
      setRoles([]);
    };
  }, []);

  const extractUserInfo = () => {
    if (keycloak.tokenParsed) {
      const tokenRoles = keycloak.tokenParsed.realm_access?.roles || [];
      const appRoles = tokenRoles.filter((r) =>
        ['CUSTOMER', 'AGENT', 'MANAGER'].includes(r)
      );
      setRoles(appRoles);
      setUser({
        id: keycloak.tokenParsed.sub,
        name: keycloak.tokenParsed.name || keycloak.tokenParsed.preferred_username,
        email: keycloak.tokenParsed.email,
        username: keycloak.tokenParsed.preferred_username,
      });
    }
  };

  const login = useCallback(() => {
    keycloak.login({ redirectUri: window.location.origin + '/' });
  }, []);

  const logout = useCallback(() => {
    keycloak.logout({ redirectUri: window.location.origin + '/' });
  }, []);

  const hasRole = useCallback(
    (role) => roles.includes(role),
    [roles]
  );

  const getPrimaryRole = useCallback(() => {
    if (roles.includes('MANAGER')) return 'MANAGER';
    if (roles.includes('AGENT')) return 'AGENT';
    if (roles.includes('CUSTOMER')) return 'CUSTOMER';
    return null;
  }, [roles]);

  if (loading) {
    return (
      <div className="app-loading">
        <div className="spinner" />
        <p>Yükleniyor...</p>
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
