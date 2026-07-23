/** Singleton Keycloak JS client instance configured from environment variables for SSO authentication. */
import Keycloak from 'keycloak-js'
import { installSessionHandlers } from './session'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'inventory',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'inventory-frontend',
})

// Se conectan aqui, junto a la instancia, y no en AuthContext: los callbacks deben estar puestos
// antes de `init()`, que se lanza a nivel de modulo, y cualquier consumidor que importe el cliente
// obtiene el mismo manejo de caducidad sin tener que acordarse de instalarlo.
installSessionHandlers(keycloak)

export default keycloak
