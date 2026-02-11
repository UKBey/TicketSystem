import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: "http://localhost:8080",
  realm: "TicketSystem",
  clientId: "ticket-frontend",
});

export default keycloak;