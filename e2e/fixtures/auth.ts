import { test as base, Page } from '@playwright/test'

export async function loginAs(page: Page, username: string, password: string) {
  await page.goto('/')
  // Wait for Keycloak redirect
  await page.waitForURL(/localhost:8180/, { timeout: 10000 })
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('#kc-login')
  // Wait for redirect back to app
  await page.waitForURL(/localhost:3000/, { timeout: 15000 })
  await page.waitForLoadState('networkidle')
}

export const test = base
export { expect } from '@playwright/test'
