import { vi } from 'vitest'
import { installSessionHandlers, MIN_TOKEN_VALIDITY_SECONDS } from '@/lib/session'
import type { SessionClient } from '@/lib/session'

/**
 * SEC-2 — expiracion de sesiones.
 *
 * Antes de esto el unico manejo de caducidad vivia en el interceptor de respuesta de `api.ts`:
 * reactivo, y solo se disparaba si el usuario hacia una peticion. Con la pestana abierta y sin
 * actividad el token caducaba en silencio, y el primer clic despues de un rato fallaba con 401.
 * Peor: si tambien habia caducado la sesion SSO, la renovacion era imposible y el usuario se
 * quedaba en una interfaz que aparentaba estar autenticada.
 */

function fakeClient(overrides: Partial<SessionClient> = {}): SessionClient {
  return {
    updateToken: vi.fn().mockResolvedValue(true),
    login: vi.fn(),
    clearToken: vi.fn(),
    ...overrides,
  } as SessionClient
}

describe('installSessionHandlers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Verifica que al caducar el token se intenta renovarlo sin esperar a que falle una peticion.
  it('renueva el token cuando Keycloak avisa de que ha caducado', async () => {
    const client = fakeClient()

    installSessionHandlers(client)
    await client.onTokenExpired?.()

    expect(client.updateToken).toHaveBeenCalledWith(MIN_TOKEN_VALIDITY_SECONDS)
    expect(client.login).not.toHaveBeenCalled()
  })

  // Verifica que si la renovacion falla se fuerza un login nuevo: la sesion SSO ya no vale.
  it('manda al login cuando la renovacion falla', async () => {
    const client = fakeClient({
      updateToken: vi.fn().mockRejectedValue(new Error('refresh token expired')),
    })

    installSessionHandlers(client)
    await client.onTokenExpired?.()

    expect(client.login).toHaveBeenCalledTimes(1)
  })

  // Verifica que el token muerto se descarta antes de redirigir, para que nada lo reutilice.
  it('descarta el token caducado antes de redirigir al login', async () => {
    const order: string[] = []
    const client = fakeClient({
      updateToken: vi.fn().mockRejectedValue(new Error('refresh token expired')),
      clearToken: vi.fn(() => {
        order.push('clearToken')
      }),
      login: vi.fn(() => {
        order.push('login')
      }),
    })

    installSessionHandlers(client)
    await client.onTokenExpired?.()

    expect(order).toEqual(['clearToken', 'login'])
  })

  // Verifica que un fallo de refresco notificado por el propio Keycloak tambien cierra la sesion.
  it('cierra la sesion cuando Keycloak notifica un error de refresco', () => {
    const client = fakeClient()

    installSessionHandlers(client)
    client.onAuthRefreshError?.()

    expect(client.clearToken).toHaveBeenCalledTimes(1)
    expect(client.login).toHaveBeenCalledTimes(1)
  })

  // Verifica que una renovacion que responde `false` no se trata como fallo: el token seguia vigente.
  it('no redirige cuando el token todavia era valido', async () => {
    const client = fakeClient({ updateToken: vi.fn().mockResolvedValue(false) })

    installSessionHandlers(client)
    await client.onTokenExpired?.()

    expect(client.login).not.toHaveBeenCalled()
    expect(client.clearToken).not.toHaveBeenCalled()
  })
})
