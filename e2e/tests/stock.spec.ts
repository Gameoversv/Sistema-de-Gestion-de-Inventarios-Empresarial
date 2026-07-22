import { test, expect } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'

test.describe('Stock y Movimientos', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'inv_admin', 'Admin123')
  })

  test('historial de movimientos se carga', async ({ page }) => {
    await page.goto('/stock')
    await page.waitForLoadState('networkidle')

    const table = page.locator('table').first()
    await expect(table).toBeVisible()
  })

  test('registrar movimiento de entrada', async ({ page }) => {
    await page.goto('/stock')
    await page.waitForLoadState('networkidle')

    // The form is always visible in the left panel (no button needed to open it)
    // Select first available product
    await page.selectOption('select[name="productId"]', { index: 1 })

    // Type is IN by default; quantity is 1 by default
    // Explicitly set to be safe
    await page.selectOption('select[name="type"]', 'IN')
    await page.fill('input[name="quantity"]', '1')

    // Submit form
    await page.click('button:has-text("Registrar movimiento")')

    // Success toast
    await expect(page.locator('text=Movimiento registrado')).toBeVisible({ timeout: 5000 })
  })

  test('exportar CSV', async ({ page }) => {
    await page.goto('/stock')
    await page.waitForLoadState('networkidle')

    // Wait for data to load (button is disabled when empty)
    await page.waitForSelector('button:has-text("Exportar CSV"):not([disabled])', { timeout: 5000 })

    const downloadPromise = page.waitForEvent('download')
    await page.click('button:has-text("Exportar CSV")')

    const download = await downloadPromise
    expect(download.suggestedFilename()).toMatch(/movimientos-stock-\d{4}-\d{2}-\d{2}\.csv/)
  })
})
