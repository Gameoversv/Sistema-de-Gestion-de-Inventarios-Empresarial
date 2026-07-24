/** Manejo de expiracion de sesion: renueva el token antes de que caduque y fuerza login cuando ya no se puede renovar. */

import { LOGIN_SCOPE } from './scopes'

/**
 * Margen, en segundos, que se le pide a Keycloak al renovar. El token se refresca si le queda
 * menos que esto de vida. Coincide con el que ya usaba el interceptor de `api.ts`, para que una
 * renovacion reactiva y una proactiva se comporten igual.
 */
export const MIN_TOKEN_VALIDITY_SECONDS = 30

/**
 * Lo que este modulo necesita de Keycloak. Se declara aparte del tipo de `keycloak-js` para poder
 * probarlo con un doble: la instancia real es un singleton que se inicializa contra un servidor.
 */
export interface SessionClient {
  updateToken(minValidity: number): Promise<boolean>
  login(options?: { scope?: string }): void
  clearToken(): void
  onTokenExpired?: () => void
  onAuthRefreshError?: () => void
}

/**
 * Conecta los dos avisos de caducidad que emite Keycloak.
 *
 * Sin esto, la unica reaccion a un token caducado era el interceptor de respuesta de `api.ts`,
 * que solo actua si el usuario lanza una peticion. Con la pestana abierta y sin actividad la
 * sesion moria en silencio y el primer clic despues fallaba con 401.
 */
export function installSessionHandlers(client: SessionClient): void {
  client.onTokenExpired = () => {
    // No se devuelve la promesa: Keycloak invoca este callback sin esperarla. El `void` deja
    // claro que el rechazo se maneja dentro y no se escapa como unhandled rejection.
    void client.updateToken(MIN_TOKEN_VALIDITY_SECONDS).catch(() => endSession(client))
  }

  // Keycloak lo emite cuando el propio refresco falla, incluido el caso en que la sesion SSO
  // haya caducado. Sin este manejador el usuario se queda ante una interfaz que aparenta estar
  // autenticada pero cuyo token ya no vale.
  client.onAuthRefreshError = () => endSession(client)
}

/**
 * Descarta el token antes de redirigir. El orden importa: si se redirige primero, el token muerto
 * sigue en memoria y cualquier peticion en vuelo lo reutiliza y recibe un 401 innecesario.
 */
export function endSession(client: SessionClient): void {
  client.clearToken()
  // Se re-solicitan los scopes de negocio; si no, tras un re-login la interfaz quedaria vacia.
  client.login({ scope: LOGIN_SCOPE })
}
