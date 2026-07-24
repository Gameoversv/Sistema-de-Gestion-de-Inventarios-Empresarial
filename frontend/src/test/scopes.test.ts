import { describe, it, expect } from 'vitest'
import { permittedScopesForRoles } from '@/lib/scopes'

describe('permittedScopesForRoles', () => {
  it('devuelve los siete scopes para inventory-admin', () => {
    // Arrange
    const roles = ['inventory-admin']

    // Act
    const scopes = permittedScopesForRoles(roles)

    // Assert
    expect(scopes.size).toBe(7)
    expect(scopes.has('user:manage')).toBe(true)
    expect(scopes.has('audit:view')).toBe(true)
  })

  it('devuelve solo lectura para viewer', () => {
    const scopes = permittedScopesForRoles(['viewer'])

    expect([...scopes].sort()).toEqual(['product:view', 'report:view', 'stock:view'])
  })

  // El corazón de G-3a: un usuario con dos roles debe recibir la UNIÓN, no el primero.
  it('une los scopes de todos los roles reconocidos', () => {
    // warehouse-clerk aporta manage; auditor aporta audit:view. Ninguno solo los tiene ambos.
    const scopes = permittedScopesForRoles(['warehouse-clerk', 'auditor'])

    expect(scopes.has('stock:manage')).toBe(true) // de warehouse-clerk
    expect(scopes.has('audit:view')).toBe(true) // de auditor
  })

  it('no depende del orden de los roles', () => {
    const a = permittedScopesForRoles(['auditor', 'warehouse-clerk'])
    const b = permittedScopesForRoles(['warehouse-clerk', 'auditor'])

    expect([...a].sort()).toEqual([...b].sort())
  })

  it('ignora los roles no reconocidos sin conceder nada por ellos', () => {
    const scopes = permittedScopesForRoles(['algún-rol-desconocido'])

    expect(scopes.size).toBe(0)
  })

  it('deniega por defecto cuando no hay ningún rol', () => {
    const scopes = permittedScopesForRoles([])

    expect(scopes.size).toBe(0)
  })
})
