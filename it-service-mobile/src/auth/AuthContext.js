import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
} from 'react';
import * as SecureStore from 'expo-secure-store';
import * as oidc from './oidc';
import api, { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { APP_ROLES } from '../config';

const REFRESH_KEY = 'kc_refresh_token';
const AuthContext = createContext(null);

/**
 * Kimlik durumunu yöneten context — web'deki AuthContext'in mobil karşılığı.
 * Access token bellekte (api client'a enjekte edilir), refresh token SecureStore'da.
 */
export function AuthProvider({ children }) {
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const refreshTokenRef = useRef(null);

  // TokenResponse'u uygula: access token'ı dağıt, claim'leri çöz, refresh'i sakla.
  const applyTokens = useCallback(async (tokenResponse) => {
    const access = tokenResponse.accessToken;
    setAuthToken(access);

    const claims = oidc.decodeJwt(access) || {};
    const tokenRoles = (claims.realm_access?.roles || []).filter((r) =>
      APP_ROLES.includes(r),
    );
    setRoles(tokenRoles);

    // Ad/soyad token claim'lerinden alınır (given_name/family_name); yoksa
    // tam ad ilk boşluktan bölünür — web ProfilePage ile aynı davranış.
    const fullName = claims.name || claims.preferred_username || '';
    let firstName = claims.given_name || '';
    let lastName = claims.family_name || '';
    if (!firstName && !lastName && fullName) {
      const sp = fullName.indexOf(' ');
      if (sp === -1) {
        firstName = fullName;
      } else {
        firstName = fullName.slice(0, sp);
        lastName = fullName.slice(sp + 1).trim();
      }
    }

    setUser({
      id: claims.sub,
      name: fullName,
      firstName,
      lastName,
      email: claims.email,
      username: claims.preferred_username,
    });
    setAuthenticated(true);

    if (tokenResponse.refreshToken) {
      refreshTokenRef.current = tokenResponse.refreshToken;
      await SecureStore.setItemAsync(REFRESH_KEY, tokenResponse.refreshToken);
    }
  }, []);

  const clearSession = useCallback(async () => {
    setAuthToken(null);
    setAuthenticated(false);
    setUser(null);
    setRoles([]);
    refreshTokenRef.current = null;
    await SecureStore.deleteItemAsync(REFRESH_KEY);
  }, []);

  // Açılışta: kayıtlı refresh token varsa sessizce yenileyip oturumu sürdür.
  useEffect(() => {
    (async () => {
      try {
        const saved = await SecureStore.getItemAsync(REFRESH_KEY);
        if (saved) {
          const refreshed = await oidc.refresh(saved);
          await applyTokens(refreshed);
          api.post('/users/sync').catch(() => {});
        }
      } catch {
        await clearSession();
      } finally {
        setLoading(false);
      }
    })();
  }, [applyTokens, clearSession]);

  // 401 yakalanınca: token yenilemeyi dene, olmazsa oturumu kapat.
  useEffect(() => {
    setUnauthorizedHandler(async () => {
      const rt = refreshTokenRef.current;
      if (!rt) {
        await clearSession();
        return;
      }
      try {
        const refreshed = await oidc.refresh(rt);
        await applyTokens(refreshed);
      } catch {
        await clearSession();
      }
    });
  }, [applyTokens, clearSession]);

  const login = useCallback(async () => {
    const tokenResponse = await oidc.login();
    if (!tokenResponse) return false;
    await applyTokens(tokenResponse);
    api.post('/users/sync').catch(() => {});
    return true;
  }, [applyTokens]);

  const logout = useCallback(async () => {
    const rt = refreshTokenRef.current;
    await clearSession();
    if (rt) oidc.endSession(rt);
  }, [clearSession]);

  // Profil güncellemesi sonrası token'ı tazeleyip user/roles'u yeniden türetir.
  const refreshUser = useCallback(async () => {
    const rt = refreshTokenRef.current;
    if (!rt) return;
    try {
      const refreshed = await oidc.refresh(rt);
      await applyTokens(refreshed);
    } catch {
      // yoksay — mevcut oturum geçerli kalır
    }
  }, [applyTokens]);

  const hasRole = useCallback((role) => roles.includes(role), [roles]);

  const hasAnyRole = useCallback(
    (...wanted) => wanted.some((r) => roles.includes(r)),
    [roles],
  );

  // Yetenek yardımcıları (additive çoklu rol — etkin yetki = rollerin birleşimi).
  // LEAD_AGENT, AGENT'ı kapsar (Keycloak composite); etkin yetki rollerin birleşimidir.
  const isAdmin = roles.includes('ADMIN');
  const isManager = roles.includes('MANAGER');
  const isLeadAgent = roles.includes('LEAD_AGENT');
  const isAgent = roles.includes('AGENT') || isLeadAgent;
  const isCustomer = roles.includes('CUSTOMER');
  const isStaff = isAgent || isAdmin || isManager;

  // Birincil/landing rol önceliği — web ile aynı: ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER.
  // Erişim her zaman rollerin birleşimine göredir.
  const getPrimaryRole = useCallback(() => {
    if (roles.includes('ADMIN')) return 'ADMIN';
    if (roles.includes('MANAGER')) return 'MANAGER';
    if (roles.includes('LEAD_AGENT')) return 'LEAD_AGENT';
    if (roles.includes('AGENT')) return 'AGENT';
    if (roles.includes('CUSTOMER')) return 'CUSTOMER';
    return null;
  }, [roles]);

  return (
    <AuthContext.Provider
      value={{
        loading,
        authenticated,
        user,
        roles,
        login,
        logout,
        refreshUser,
        hasRole,
        hasAnyRole,
        isAdmin,
        isManager,
        isLeadAgent,
        isAgent,
        isCustomer,
        isStaff,
        getPrimaryRole,
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
