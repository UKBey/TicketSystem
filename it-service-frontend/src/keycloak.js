import Keycloak from 'keycloak-js';

// Nginx reverse proxy uzerinden geldiginden URL her zaman window.location.origin + /auth'dir.
// KC_HTTP_RELATIVE_PATH=/auth sayesinde Keycloak tum endpoint'lerini /auth/ altinda sunar.
const keycloak = new Keycloak({
  url: window.location.origin + '/auth',
  realm: 'TicketSystemRealm',
  clientId: 'ticket-frontend',
});

export default keycloak;
