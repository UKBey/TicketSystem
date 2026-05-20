import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';
import { KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_CLIENT_ID } from '../config';

// Tarayıcı oturumunu redirect dönüşünde tamamlar — modül yüklenirken bir kez çağrılır.
WebBrowser.maybeCompleteAuthSession();

const realmUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`;

/**
 * Keycloak OIDC endpoint'leri elle kuruluyor — discovery doc'a güvenmiyoruz çünkü
 * Keycloak döndürdüğü URL'leri KC_HOSTNAME ile sabitler ve telefon LAN IP'sine
 * erişemez. Burada HOST (LAN IP) üzerinden gidiyoruz.
 */
export const discovery = {
  authorizationEndpoint: `${realmUrl}/protocol/openid-connect/auth`,
  tokenEndpoint: `${realmUrl}/protocol/openid-connect/token`,
  endSessionEndpoint: `${realmUrl}/protocol/openid-connect/logout`,
  userInfoEndpoint: `${realmUrl}/protocol/openid-connect/userinfo`,
};

/** Deep-link redirect URI — Keycloak ticket-mobile client'ına kayıtlı olmalı. */
export const redirectUri = AuthSession.makeRedirectUri({ scheme: 'itservicemobile' });

// İlk kurulumda Keycloak client'ına doğru pattern'i eklemek için redirect URI'yi logla.
if (__DEV__) {
  console.log('[oidc] redirectUri =', redirectUri);
}

const SCOPES = ['openid', 'profile', 'email'];

/**
 * Tarayıcı tabanlı OIDC login — authorization code + PKCE.
 * Başarıda TokenResponse, iptal/hatada null döner.
 */
export async function login() {
  const request = new AuthSession.AuthRequest({
    clientId: KEYCLOAK_CLIENT_ID,
    redirectUri,
    scopes: SCOPES,
    usePKCE: true,
  });
  await request.makeAuthUrlAsync(discovery);

  const result = await request.promptAsync(discovery);
  if (result.type !== 'success' || !result.params?.code) {
    return null;
  }

  return AuthSession.exchangeCodeAsync(
    {
      clientId: KEYCLOAK_CLIENT_ID,
      code: result.params.code,
      redirectUri,
      extraParams: { code_verifier: request.codeVerifier },
    },
    discovery,
  );
}

/** Refresh token ile yeni bir access token alır. */
export async function refresh(refreshToken) {
  return AuthSession.refreshAsync(
    { clientId: KEYCLOAK_CLIENT_ID, refreshToken },
    discovery,
  );
}

/** Keycloak oturumunu sonlandırır (best-effort — başarısız olsa da local temizlik yapılır). */
export async function endSession(refreshToken) {
  try {
    await AuthSession.revokeAsync(
      {
        clientId: KEYCLOAK_CLIENT_ID,
        token: refreshToken,
        tokenTypeHint: AuthSession.TokenTypeHint.RefreshToken,
      },
      discovery,
    );
  } catch {
    // yoksay
  }
}

/**
 * JWT payload'unu çözer — imza DOĞRULANMAZ, yalnızca claim okumak için
 * (roller, sub, isim). İmza doğrulamasını backend yapar.
 */
export function decodeJwt(token) {
  try {
    const part = token.split('.')[1];
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    // escape+atob — UTF-8 karakterleri (Türkçe isim vb.) doğru çözülsün.
    const json = decodeURIComponent(escape(global.atob(padded)));
    return JSON.parse(json);
  } catch {
    return null;
  }
}
