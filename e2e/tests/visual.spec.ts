import { test, expect } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'

// TEST-8 — Regresión visual con snapshots de Playwright. Se captura sobre
// regiones estables (el sidebar de navegación y el formulario de alta de
// producto), no sobre tablas con datos que cambian entre corridas, para que la
// comparación detecte cambios de UI reales y no ruido de datos.
//
// Los tests llevan el tag @visual: el workflow los separa del resto para poder
// generar las imágenes de referencia en CI (donde sí se puede levantar el stack).

test.describe('Regresión visual @visual', () => {
  test('sidebar @visual', async ({ page }) => {
    await loginAs(page, 'inv_admin', 'Admin123')
    await expect(page.locator('aside')).toHaveScreenshot('sidebar.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    })
  })

  test('formulario de alta de producto @visual', async ({ page }) => {
    await loginAs(page, 'inv_admin', 'Admin123')
    await page.click('a[href="/products"]')
    await page.waitForLoadState('networkidle')
    await page.click('button:has-text("Nuevo producto")')
    // El modal/formulario vacío es estable: campos fijos, sin datos.
    const form = page.locator('form').first()
    await expect(form).toHaveScreenshot('product-form.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    })
  })
})
