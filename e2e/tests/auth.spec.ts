import { test, expect } from '../fixtures/auth'

test.describe('Autenticacion', () => {
  test('login exitoso como admin', async ({ page }) => {
    await page.goto('/')
    await page.waitForURL(/localhost:8180/, { timeout: 10000 })

    await page.fill('#username', 'inv_admin')
    await page.fill('#password', 'admin123')
    await page.click('#kc-login')

    await page.waitForURL(/localhost:3000/, { timeout: 15000 })
    await page.waitForLoadState('networkidle')

    // Sidebar is an <aside> element
    await expect(page.locator('aside')).toBeVisible()
  })

  test('login fallido muestra error', async ({ page }) => {
    await page.goto('/')
    await page.waitForURL(/localhost:8180/, { timeout: 10000 })

    await page.fill('#username', 'inv_admin')
    await page.fill('#password', 'wrong_password')
    await page.click('#kc-login')

    // Keycloak shows error message
    const errorMessage = page.locator('.alert-error, #input-error, [class*="error"]').first()
    await expect(errorMessage).toBeVisible({ timeout: 5000 })
  })

  test('logout cierra sesion', async ({ page }) => {
    await page.goto('/')
    await page.waitForURL(/localhost:8180/, { timeout: 10000 })
    await page.fill('#username', 'inv_admin')
    await page.fill('#password', 'admin123')
    await page.click('#kc-login')
    await page.waitForURL(/localhost:3000/, { timeout: 15000 })
    await page.waitForLoadState('networkidle')

    // Logout button is icon-only with title="Cerrar sesión" in sidebar
    await page.click('button[title="Cerrar sesión"]')

    await page.waitForURL(/localhost:8180/, { timeout: 10000 })
    await expect(page.locator('#username')).toBeVisible()
  })
})
