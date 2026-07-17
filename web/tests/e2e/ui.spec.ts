import { expect, test } from '@playwright/test'

const pages = [
  ['/dashboard', '履约数据总览'],
  ['/shipments', '运单管理'],
  ['/events', '原始事件'],
  ['/anomalies', '异常中心'],
  ['/reconciliation', '对账任务'],
  ['/carriers', '物流商配置'],
  ['/simulation', '故障模拟'],
] as const

test.describe('TrackFlow console UI', () => {
  test('renders core pages without layout overflow', async ({ page }) => {
    const consoleErrors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })

    for (const [path, title] of pages) {
      await page.goto(path)
      await expect(page.locator('.page-title')).toHaveText(title)
      await expect(page.locator('.nav-item')).toHaveCount(7)

      const hasOverflow = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
      )
      expect(hasOverflow, `${path} should not overflow horizontally`).toBe(false)
    }

    expect(consoleErrors).toEqual([])
  })

  test('keeps mobile layout in a single readable column', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/simulation')
    await expect(page.locator('.page-title')).toHaveText('故障模拟')

    const layout = await page.evaluate(() => ({
      hasOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
      formColumns: getComputedStyle(document.querySelector('.form-grid') as Element).gridTemplateColumns,
    }))

    expect(layout.hasOverflow).toBe(false)
    expect(layout.formColumns.split(' ').length).toBe(1)
  })

  test('runs the simulation flow when the API is available', async ({ page, request }) => {
    const health = await request.get('http://127.0.0.1:8002/actuator/health', { timeout: 2_000 }).catch(() => null)
    test.skip(!health?.ok(), 'TrackFlow API is not running on 8002')

    await page.goto('/simulation')
    await page.getByRole('button', { name: '执行场景' }).click()

    await expect(page.getByText('场景执行完成')).toBeVisible()
    await expect(page.locator('.json-view')).toBeVisible()
    await expect(page.locator('.summary-item')).toHaveCount(4)
  })
})
