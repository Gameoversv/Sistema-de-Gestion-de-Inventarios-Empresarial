import { describe, it, expect } from 'vitest'
import { LOGIN_SCOPE } from '@/lib/scopes'

/**
 * El mapa rol→scopes que este fichero probaba se retiró en G-2: el token ya llega recortado por
 * rol desde Keycloak y el cliente confía en él (ADR-004). Lo que queda por proteger es
 * `LOGIN_SCOPE`, que sigue siendo frágil por una razón concreta: los scopes de negocio son
 * *optional client scopes*, así que uno que no se pida no llega al token, y `PermissionGuard`
 * oculta su sección sin ningún error visible. Ese fue exactamente el bug #69.
 */
describe('LOGIN_SCOPE', () => {
  const requested = LOGIN_SCOPE.split(' ').filter(Boolean)

  it('pide los seis scopes de negocio que protegen la interfaz', () => {
    // Arrange
    const expected = [
      'product:view',
      'product:manage',
      'stock:view',
      'stock:manage',
      'report:view',
      'audit:view',
    ]

    // Act
    const missing = expected.filter((scope) => !requested.includes(scope))

    // Assert
    expect(missing).toEqual([])
  })

  // user:manage existe en el realm pero no protege ningún endpoint (A-2, issue #48). Pedirlo
  // solo añadiría una línea a la pantalla de consentimiento sin habilitar nada.
  it('no pide user:manage, que hoy no protege ningún endpoint', () => {
    expect(requested).not.toContain('user:manage')
  })

  // keycloak-js añade openid por su cuenta; duplicarlo aquí no rompe, pero delata que alguien
  // copió la cadena sin entender de dónde sale.
  it('no repite openid, que lo añade keycloak-js', () => {
    expect(requested).not.toContain('openid')
  })

  it('no trae entradas vacías ni duplicadas', () => {
    expect(new Set(requested).size).toBe(requested.length)
    expect(requested.every((scope) => scope.trim().length > 0)).toBe(true)
  })
})
