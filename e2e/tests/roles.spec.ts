import { test, expect, loginAs } from '../fixtures/auth'
import type { Page } from '@playwright/test'

/**
 * E2E por rol — el enunciado lo exige de forma literal: "E2E Testing debe incluir: Snapshots,
 * Flujos completos, Navegación, **Roles**, Seguridad y Responsive testing". El resto de specs
 * entra siempre como `inv_admin`, así que ninguno demostraba que la interfaz se restrinja.
 *
 * Dos decisiones de método que importan aquí:
 *
 * 1. **Se afirma lo que cada rol SÍ ve, no solo lo que no ve.** `PermissionGuard` falla del lado
 *    seguro: ante la duda oculta. Un test que solo comprobara ausencias pasaría igual con la
 *    interfaz entera en blanco, que es justo el sintoma del bug #69.
 *
 * 2. **Se navega por URL directa, no solo por el menú.** El bug #70 era que `check-sso` perdía
 *    los optional scopes al recargar: por sidebar funcionaba y con `page.goto` no. Comprobar el
 *    acceso directo cubre además la palabra "Seguridad" del mismo requisito.
 */

const USERS = {
  admin: { user: 'inv_admin', pass: process.env.KC_USER_ADMIN_PASSWORD ?? 'Admin123' },
  clerk: { user: 'inv_clerk', pass: process.env.KC_USER_CLERK_PASSWORD ?? 'Clerk123' },
  auditor: { user: 'inv_auditor', pass: process.env.KC_USER_AUDITOR_PASSWORD ?? 'Auditor123' },
  viewer: { user: 'inv_viewer', pass: process.env.KC_USER_VIEWER_PASSWORD ?? 'Viewer123' },
}

/** Enlaces del sidebar visibles para el usuario que tenga la sesión abierta. */
async function navLinks(page: Page): Promise<string[]> {
  await expect(page.locator('aside')).toBeVisible()
  return page.locator('aside nav a').allInnerTexts()
}

test.describe('Roles — la interfaz se restringe por permiso', () => {
  // Cada caso abre sesión propia: los scopes viven en el token, así que compartir contexto
  // entre roles daría falsos verdes.
  test.use({ storageState: { cookies: [], origins: [] } })

  test('admin ve las cinco secciones y puede gestionar', async ({ page }) => {
    await loginAs(page, USERS.admin.user, USERS.admin.pass)

    const links = await navLinks(page)
    expect(links.join(' ')).toContain('Auditoría')
    // user:manage solo lo tiene el admin: la sección de usuarios es su marca distintiva.
    expect(links.join(' ')).toContain('Usuarios')

    await page.goto('/products')
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toBeVisible()

    await page.goto('/stock')
    await expect(page.getByRole('heading', { name: 'Registrar movimiento' })).toBeVisible()
  })

  test('clerk gestiona productos y stock, pero no ve Auditoría', async ({ page }) => {
    await loginAs(page, USERS.clerk.user, USERS.clerk.pass)

    // Lo que SÍ debe poder hacer: es la mitad del contrato que un test de ausencias no cubre.
    await page.goto('/products')
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toBeVisible()

    await page.goto('/stock')
    await expect(page.getByRole('heading', { name: 'Registrar movimiento' })).toBeVisible()

    const links = await navLinks(page)
    expect(links.join(' ')).not.toContain('Auditoría')
    expect(links.join(' ')).not.toContain('Usuarios')
  })

  test('auditor consulta auditoría pero no gestiona nada', async ({ page }) => {
    await loginAs(page, USERS.auditor.user, USERS.auditor.pass)

    const links = await navLinks(page)
    expect(links.join(' ')).toContain('Auditoría')
    expect(links.join(' ')).not.toContain('Usuarios')

    await page.goto('/products')
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toHaveCount(0)

    await page.goto('/stock')
    await expect(page.getByRole('heading', { name: 'Registrar movimiento' })).toHaveCount(0)
  })

  test('viewer solo lee: sin gestión y sin Auditoría', async ({ page }) => {
    await loginAs(page, USERS.viewer.user, USERS.viewer.pass)

    // Ve lo que su rol permite...
    const links = await navLinks(page)
    expect(links.join(' ')).toContain('Productos')
    expect(links.join(' ')).toContain('Stock')

    // ...y nada más.
    expect(links.join(' ')).not.toContain('Auditoría')
    expect(links.join(' ')).not.toContain('Usuarios')

    await page.goto('/products')
    await expect(page.getByRole('button', { name: 'Nuevo producto' })).toHaveCount(0)

    await page.goto('/stock')
    await expect(page.getByRole('heading', { name: 'Registrar movimiento' })).toHaveCount(0)
  })
})

test.describe('Seguridad — el acceso directo por URL respeta el permiso', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  /**
   * Regresión del bug #70. `page.goto` fuerza recarga completa, y era ahí donde el SSO silencioso
   * devolvía un token sin los optional scopes y la interfaz protegida desaparecía para todos.
   * Si vuelve a pasar, el auditor deja de ver su propia sección y este test se pone rojo.
   */
  test('el auditor conserva sus scopes tras navegar directo a /audit', async ({ page }) => {
    await loginAs(page, USERS.auditor.user, USERS.auditor.pass)

    await page.goto('/audit')
    await page.waitForLoadState('networkidle')

    await expect(page.locator('aside')).toBeVisible()
    expect((await navLinks(page)).join(' ')).toContain('Auditoría')
  })

  test('el viewer que entra directo a /audit no obtiene la sección', async ({ page }) => {
    await loginAs(page, USERS.viewer.user, USERS.viewer.pass)

    await page.goto('/audit')
    await page.waitForLoadState('networkidle')

    // La sesión sigue viva -el sidebar se pinta-, pero la sección no le pertenece.
    await expect(page.locator('aside')).toBeVisible()
    expect((await navLinks(page)).join(' ')).not.toContain('Auditoría')
  })
})
