import { expect, test } from '@playwright/test'

for (const width of [320, 375, 414, 768, 1280, 1440]) {
  test(`overview remains operable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '校园运行脉搏' })).toBeVisible()
    await expect(page.locator('body')).toHaveJSProperty('scrollWidth', width)
    await page.screenshot({ path: `../../design/visual-tests/admin-${width}.png`, fullPage: true })
  })
}

test('mobile navigation and content route work', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 })
  await page.goto('/')
  await page.getByRole('button', { name: '打开导航' }).click()
  await page.getByRole('link', { name: /举报审核/ }).click()
  await expect(page.getByRole('heading', { name: '举报审核' })).toBeVisible()
})

test('data search filters locally while protected writes stay disabled', async ({ page }) => {
  await page.goto('/reports')
  await page.getByLabel('搜索当前结果').fill('G-992')
  await expect(page.getByText('商品 #G-992')).toBeVisible()
  await expect(page.getByText('帖子 #P-3821')).toBeHidden()
  await page.getByRole('checkbox', { name: /选择 商品 #G-992/ }).check()
  await expect(page.getByText('已选 1 项')).toBeVisible()
  await expect(page.getByRole('button', { name: /批量处理/ })).toBeDisabled()
  await expect(page.getByRole('button', { name: /审核规则/ })).toBeDisabled()
})

test('login reports missing environment safely', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill('admin@campus.ai')
  await page.getByLabel('密码').fill('password123')
  await page.getByRole('button', { name: /登录管理台/ }).click()
  await expect(page.getByRole('alert')).toContainText('尚未配置 Supabase')
})
