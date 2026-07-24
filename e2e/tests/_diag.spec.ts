import { test } from '../fixtures/auth'
import { loginAs } from '../fixtures/auth'

// DIAGNOSTICO TEMPORAL (C-1): captura el Bearer real que usa el browser y
// comprueba si el formulario de movimiento (gated stock:manage) se renderiza.
test('DIAG token del browser en /stock', async ({ page }) => {
  let bearer = ''
  page.on('request', (req) => {
    const a = req.headers()['authorization']
    if (a?.startsWith('Bearer ') && !bearer) bearer = a.slice(7)
  })

  await loginAs(page, 'inv_admin', 'Admin123')
  await page.goto('/stock')
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(2000)

  if (bearer) {
    const payload = JSON.parse(Buffer.from(bearer.split('.')[1], 'base64').toString())
    console.log('DIAG BROWSER SCOPE:', payload.scope)
    console.log('DIAG BROWSER ROLES:', JSON.stringify(payload.realm_access?.roles))
  } else {
    console.log('DIAG BROWSER: no se capturo ningun Bearer')
  }

  const formVisible = await page
    .locator('h3:has-text("Registrar movimiento")')
    .isVisible()
    .catch(() => false)
  console.log('DIAG FORM VISIBLE:', formVisible)

  const selectCount = await page.locator('select[name="productId"]').count()
  console.log('DIAG SELECT COUNT:', selectCount)
})
