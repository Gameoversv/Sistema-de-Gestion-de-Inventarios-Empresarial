import { test, expect } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'

// TEST-9 — Responsive testing en los tres breakpoints que pide el enunciado:
// móvil (375), tablet (768) y escritorio (1440). La comprobación clave es que
// ninguna página desborde horizontalmente: el contenido ancho (tablas) debe
// hacer scroll dentro de su propio contenedor, no empujar el body.

const VIEWPORTS = [
  { name: 'móvil', width: 375, height: 667 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'escritorio', width: 1440, height: 900 },
]

async function horizontalOverflow(page: import('@playwright/test').Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
}

for (const vp of VIEWPORTS) {
  test.describe(`Responsive ${vp.name} (${vp.width}px)`, () => {
    test.use({ viewport: { width: vp.width, height: vp.height } })

    test('el dashboard no desborda horizontalmente', async ({ page }) => {
      await loginAs(page, 'inv_admin', 'Admin123')
      await page.waitForLoadState('networkidle')
      // Tolerancia de 1px por redondeos de subpíxel.
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(1)
    })

    test('la tabla de productos no desborda horizontalmente', async ({ page }) => {
      await loginAs(page, 'inv_admin', 'Admin123')
      await page.click('a[href="/products"]')
      await page.waitForLoadState('networkidle')
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(1)
    })
  })
}
