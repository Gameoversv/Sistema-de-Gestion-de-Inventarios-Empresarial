import { test, expect } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'

// NOTE: page.goto('/products') is intercepted by Vite's proxy (sends to backend, not SPA).
// All product navigation uses SPA routing (click sidebar link from '/').

test.describe('Productos', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'inv_admin', 'admin123')
    // loginAs lands on '/' (dashboard). Navigate via React Router (sidebar).
    await page.click('a[href="/products"]')
    await page.waitForLoadState('networkidle')
  })

  test('lista de productos se carga', async ({ page }) => {
    const table = page.locator('table').first()
    await expect(table).toBeVisible({ timeout: 10000 })

    const rows = page.locator('table tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)
  })

  test('crear producto nuevo', async ({ page }) => {
    await page.click('button:has-text("Nuevo producto")')

    const uniqueSku = `SKU-E2E-${Date.now()}`
    await page.fill('input[name="sku"]', uniqueSku)
    await page.fill('input[name="name"]', 'Producto Test E2E')
    await page.fill('input[name="price"]', '99.99')
    await page.selectOption('select[name="categoryId"]', { index: 1 })

    await page.click('button:has-text("Crear producto")')

    await expect(page.locator('text=Producto creado')).toBeVisible({ timeout: 5000 })
    await expect(page.locator(`text=${uniqueSku}`)).toBeVisible({ timeout: 5000 })
  })

  test('editar producto', async ({ page }) => {
    await page.click('button[title="Editar"]')
    await expect(page.locator('text=Editar producto')).toBeVisible()

    const updatedName = `Producto Editado E2E ${Date.now()}`
    await page.fill('input[name="name"]', updatedName)

    await page.click('button:has-text("Guardar cambios")')

    await expect(page.locator('text=Producto actualizado')).toBeVisible({ timeout: 10000 })
    await expect(page.locator(`text=${updatedName}`)).toBeVisible({ timeout: 8000 })
  })
})
