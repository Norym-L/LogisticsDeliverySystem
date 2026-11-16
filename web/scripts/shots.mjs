import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const base = 'http://127.0.0.1:18080'
mkdirSync('shots', { recursive: true })

// 审批超时只有 60 秒，截图前先补几条待审批的单，避免列表为空
async function seed() {
  const bodies = [
    { senderName: '张启明', senderPhone: '13700000001', receiverName: '李慕白', receiverPhone: '13700000002', fromStationId: 1, toStationId: 6, goodsDesc: '教材两箱', weight: 3.5 },
    { senderName: '周砚', senderPhone: '13700000003', receiverName: '陈叙', receiverPhone: '13700000004', fromStationId: 8, toStationId: 4, goodsDesc: '电子元件', weight: 0.8 },
    { senderName: '林照', senderPhone: '13700000005', receiverName: '许清和', receiverPhone: '13700000006', fromStationId: 6, toStationId: 7, goodsDesc: '样品件', weight: 1.2 }
  ]
  for (const body of bodies) {
    await fetch(base + '/api/user/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
  }
}

await seed()

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 920 } })
const page = await context.newPage()

page.on('console', (msg) => {
  if (msg.type() === 'error') console.log('[console.error]', msg.text())
})

await page.goto(base + '/login', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.screenshot({ path: 'shots/01-login.png' })

await page.fill('#username', 'admin')
await page.fill('#password', '123456')
await page.click('button[type=submit]')
await page.waitForURL(base + '/', { timeout: 15000 })
await page.waitForTimeout(1000)
await page.screenshot({ path: 'shots/02-pending.png' })

await page.locator('.order-row').first().click()
await page.waitForTimeout(900)
await page.screenshot({ path: 'shots/03-drawer.png' })

await page.getByRole('button', { name: '关闭' }).click()
await page.waitForTimeout(400)

await page.goto(base + '/postmen', { waitUntil: 'networkidle' })
await page.waitForTimeout(900)
await page.screenshot({ path: 'shots/04-postmen.png' })

await page.locator('.person').first().click()
await page.waitForTimeout(800)
await page.screenshot({ path: 'shots/05-person-tasks.png' })

// 石墨主题
await page.goto(base + '/', { waitUntil: 'networkidle' })
await page.getByRole('button', { name: '石墨' }).click()
await page.waitForTimeout(700)
await page.screenshot({ path: 'shots/06-dark.png' })

console.log('done')
await browser.close()
