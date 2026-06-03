import Constants from 'expo-constants';

/**
 * Uygulama yapılandırması.
 *
 * Expo Go ile telefon, geliştirme makinesinin LAN IP'sine bağlanır. Expo bu IP'yi
 * `hostUri` içinde verir (ör. "192.168.1.5:8081"). Backend de aynı makinede nginx
 * arkasında :80'de çalıştığı için IP'yi alıp oraya yönlendiriyoruz — elle IP
 * girmeye gerek kalmaz.
 *
 * Farklı bir backend adresi gerekiyorsa HOST_OVERRIDE'ı doldurun (ör. prod URL).
 */
const HOST_OVERRIDE = null; // örn: 'https://ticketsystem.example.com'

function resolveHost() {
  if (HOST_OVERRIDE) return HOST_OVERRIDE;
  const hostUri =
    Constants.expoConfig?.hostUri ||
    Constants.expoGoConfig?.debuggerHost ||
    '';
  const ip = hostUri.split(':')[0];
  return ip ? `http://${ip}` : 'http://localhost';
}

export const HOST = resolveHost();

/** Backend REST API kökü — nginx üzerinden /api/v1. */
export const API_BASE_URL = `${HOST}/api/v1`;

/** Keycloak — nginx üzerinden /auth (KC_HTTP_RELATIVE_PATH=/auth). */
export const KEYCLOAK_URL = `${HOST}/auth`;
export const KEYCLOAK_REALM = 'TicketSystemRealm';
/** Mobil için ayrı public client — keycloak-init realm import'una eklenir. */
export const KEYCLOAK_CLIENT_ID = 'ticket-mobile';

/**
 * Uygulamadaki anlamlı roller (token'daki realm_access.roles bunlara filtrelenir).
 * Eklemeli (additive) çoklu rol modeli; LEAD_AGENT, AGENT'ı kapsayan Keycloak composite'idir.
 */
export const APP_ROLES = ['CUSTOMER', 'AGENT', 'LEAD_AGENT', 'ADMIN', 'MANAGER'];
