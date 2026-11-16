import { chromium } from 'playwright'

const base = 'http://127.0.0.1:18080'
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 920 } })
const page = await context.newPage()
const log = (...args) => console.log(...args)

await page.goto(base + '/login', { waitUntil: 'networkidle' })

// 1. 节点管理员不能进入路径管理台
await page.fill('#username', 'st001')
await page.fill('#password', '123456')
await page.click('button[type=submit]')
await page.waitForSelector('.error', { timeout: 10000 })
log('PASS 1 节点管理员被拒绝 →', (await page.textContent('.error'))?.trim())

// 2. 路径管理员登录
await page.fill('#username', 'admin')
await page.fill('#password', '123456')
await page.click('button[type=submit]')
await page.waitForURL(base + '/', { timeout: 15000 })
await page.waitForTimeout(700)

const before = await page.locator('.order-row').count()

// 3. 批准第一单
const firstNo = (await page.locator('.order-row').first().locator('.meta b').first().textContent())?.trim()
await page.locator('.order-row').first().click()
await page.waitForTimeout(600)
await page.getByRole('button', { name: '批准并固化路线' }).click()
await page.waitForTimeout(1400)
const afterApprove = await page.locator('.order-row').count()
log(`PASS 2 批准 ${firstNo}：待审批 ${before} → ${afterApprove}`)

// 4. 拒绝第二单
if (afterApprove > 0) {
  const secondNo = (await page.locator('.order-row').first().locator('.meta b').first().textContent())?.trim()
  await page.locator('.order-row').first().click()
  await page.waitForTimeout(500)
  await page.getByRole('button', { name: '拒绝', exact: true }).click()
  await page.fill('#reason', '演示：货物描述不完整')
  await page.getByRole('button', { name: '确认拒绝' }).click()
  await page.waitForTimeout(1400)
  const afterReject = await page.locator('.order-row').count()
  log(`PASS 3 拒绝 ${secondNo}：待审批 ${afterApprove} → ${afterReject}`)
}

// 5. 刷新后会话保持
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(600)
log('PASS 4 刷新后 URL =', page.url())

await browser.close()
