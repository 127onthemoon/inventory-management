// 用 Playwright（系统 Chrome）给系统各页面截图：node scripts/shots.mjs <输出目录> [BASE] [前缀]
import { chromium } from 'playwright';
import fs from 'node:fs';
const out = process.argv[2] || 'shots'; const B = process.argv[3] || 'http://127.0.0.1:8080';
const prefix = process.argv[4] || '';
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
async function session(user, pass, vp = { width: 1440, height: 900 }) {
  const ctx = await browser.newContext({ viewport: vp, deviceScaleFactor: 1, locale: 'zh-CN' });
  const page = await ctx.newPage();
  if (user) {
    await page.goto(B + '/login');
    await page.fill('#username', user); await page.fill('#password', pass);
    await Promise.all([page.waitForNavigation(), page.click('button[type=submit]')]);
  }
  return { ctx, page };
}
async function shot(page, name, url, full = true) {
  await page.goto(B + url, { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${out}/${prefix}${name}.png`, fullPage: full });
  console.log('shot', name, url);
}
let s = await session(null);
await shot(s.page, '00-login', '/login'); await s.ctx.close();
s = await session('admin', 'admin123');
const list = [['01-dashboard', '/'], ['02-products', '/products'], ['03-product-detail', '/products/8'], ['04-product-form', '/products/new'],
  ['05-categories', '/categories'], ['06-suppliers', '/suppliers'], ['07-stock-in', '/stock/in'], ['07b-stock-in-form', '/stock/in/new?productId=8'],
  ['08-stock-out-form', '/stock/out/new?productId=8'], ['09-stock-out-list', '/stock/out'], ['10-flows', '/flows'], ['11-consistency', '/consistency'],
  ['12-users', '/users'], ['12b-user-form', '/users/new'], ['15-empty-search', '/products?keyword=__none__']];
for (const [n, u] of list) await shot(s.page, n, u);
await s.ctx.close();
s = await session('operator', 'operator123');
await shot(s.page, '13-operator-403', '/users'); await shot(s.page, '14-operator-products', '/products'); await s.ctx.close();
s = await session('admin', 'admin123', { width: 390, height: 844 });
await shot(s.page, '16-mobile-dashboard', '/', false); await shot(s.page, '17-mobile-products', '/products', false); await s.ctx.close();
await browser.close();
