import Keycloak from 'keycloak-js';

const keycloakUrl = window.location.hostname === 'localhost'
  ? 'http://localhost:8080'
  : window.location.origin;

const keycloak = new Keycloak({
  url: keycloakUrl,
  realm: 'TicketSystemRealm',
  clientId: 'ticket-frontend',
});

export default keycloak;
