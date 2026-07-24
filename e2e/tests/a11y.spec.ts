import { test, expect } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'
import AxeBuilder from '@axe-core/playwright'

// D-4 — Accesibilidad automatizada con axe-core sobre las páginas principales.
// Se gatea en violaciones 'critical' y 'serious' de WCAG 2 A/AA; las moderadas y
// menores se reportan pero no tumban el build.

async function seriousViolations(page: import('@playwright/test').Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
  return results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')
}

test.describe('Accesibilidad (axe-core, WCAG 2 A/AA)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'inv_admin', 'Admin123')
  })

  test('dashboard sin violaciones críticas ni serias', async ({ page }) => {
    const v = await seriousViolations(page)
    expect(v.map((x) => x.id).join(', ')).toBe('')
  })

  test('productos sin violaciones críticas ni serias', async ({ page }) => {
    await page.click('a[href="/products"]')
    await page.waitForLoadState('networkidle')
    const v = await seriousViolations(page)
    expect(v.map((x) => x.id).join(', ')).toBe('')
  })

  test('stock sin violaciones críticas ni serias', async ({ page }) => {
    await page.click('a[href="/stock"]')
    await page.waitForLoadState('networkidle')
    const v = await seriousViolations(page)
    expect(v.map((x) => x.id).join(', ')).toBe('')
  })
})
