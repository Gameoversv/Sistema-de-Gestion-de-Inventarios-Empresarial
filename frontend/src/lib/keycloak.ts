/** Singleton Keycloak JS client instance configured from environment variables for SSO authentication. */
import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'inventory',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'inventory-frontend',
})

export default keycloak
