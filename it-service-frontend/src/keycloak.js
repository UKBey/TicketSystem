import Keycloak from 'keycloak-js';

// Nginx reverse proxy uzerinden geldiginden URL her zaman window.location.origin + /auth'dir.
// KC_HTTP_RELATIVE_PATH=/auth sayesinde Keycloak tum endpoint'lerini /auth/ altinda sunar.
const keycloak = new Keycloak({
  url: window.location.origin + '/auth',
  realm: 'TicketSystemRealm',
  clientId: 'ticket-frontend',
});

// localStorage'daki dil tercihini 'tr' veya 'en'e normalize eder.
function resolveLanguage() {
  const stored = localStorage.getItem('language');
  return stored && stored.startsWith('tr') ? 'tr' : 'en';
}

/**
 * Keycloak giris sayfasina yonlendirir.
 *
 * keycloak-js `login()` yalnizca OIDC `ui_locales` parametresini gonderir; bu da
 * Keycloak'in locale onceliginde `KEYCLOAK_LOCALE` cookie'sinin ALTINDA kalir —
 * yani kullanici bir kez giris yaptiktan sonra cookie dili sabitler ve uygulamanin
 * yeni secimi yok sayilir. Cozum: en yuksek oncelikli `kc_locale` query parametresini
 * de eklemek (bu ayni zamanda cookie'yi de tazeler).
 *
 * `createLoginUrl` keycloak.login()'in dahili olarak kullandigi URL'i uretir, bu
 * sayede PKCE/state isleyisi korunur. keycloak-js v26'da bu metot async'tir.
 */
export async function redirectToKeycloakLogin({ redirectUri, action } = {}) {
  const locale = resolveLanguage();
  let url = await keycloak.createLoginUrl({ redirectUri, locale, action });
  // createLoginUrl her zaman query parametreli bir URL dondurur, yine de '?' kontrolu yapalim.
  url += (url.includes('?') ? '&' : '?') + 'kc_locale=' + locale;
  window.location.assign(url);
}

/**
 * Keycloak oturumunu kapatir. login akisiyla simetrik olarak `kc_locale` ekler —
 * boylece cikis sirasinda Keycloak bir sayfa render ederse uygulamanin diliyle gelir.
 * `createLogoutUrl`, id_token_hint + post_logout_redirect_uri'yi zaten ekledigi icin
 * `keycloak.logout()` ile ayni sessiz cikis davranisini korur.
 */
export async function redirectToKeycloakLogout({ redirectUri } = {}) {
  const locale = resolveLanguage();
  let url = await keycloak.createLogoutUrl({ redirectUri });
  url += (url.includes('?') ? '&' : '?') + 'kc_locale=' + locale;
  window.location.assign(url);
}

export default keycloak;
