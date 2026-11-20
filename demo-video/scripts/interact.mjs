// 交互验收：确认弹窗、库存预览、库存不足拒绝、成功 Toast、移动端抽屉导航、操作员 403
import { chromium } from 'playwright';
const out = process.argv[2] || 'shots'; const B = 'http://127.0.0.1:8080';
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
async function login(u, p, vp = { width: 1440, height: 900 }) {
  const ctx = await browser.newContext({ viewport: vp, locale: 'zh-CN' }); const page = await ctx.newPage();
  await page.goto(B + '/login'); await page.fill('#username', u); await page.fill('#password', p);
  await Promise.all([page.waitForNavigation(), page.click('button[type=submit]')]); return { ctx, page };
}
const results = [];
const ok = (name, cond) => { results.push(`${cond ? '✔' : '✘'} ${name}`); };
let { ctx, page } = await login('admin', 'admin123');
// 1. 删除确认弹窗（点取消，不真正删除）
await page.goto(B + '/products'); 
await page.locator('form[data-confirm] button').first().click();
await page.waitForSelector('dialog.confirm[open]'); await page.waitForTimeout(300);
ok('删除按钮弹出自定义确认弹窗', await page.isVisible('dialog.confirm[open]'));
await page.screenshot({ path: `${out}/20-confirm-dialog.png` });
await page.keyboard.press('Escape'); await page.waitForTimeout(200);
ok('Esc 关闭弹窗且未提交', !(await page.isVisible('dialog.confirm[open]')) && page.url().endsWith('/products'));
// 2. 出库预览 + 库存不足拒绝
await page.goto(B + '/stock/out/new?productId=8');
const stockBefore = await page.textContent('[data-p-stock]');
await page.fill('#quantity', '9999'); await page.waitForTimeout(200);
ok('出库数量超过库存时预览面板提示将被拒绝', (await page.getAttribute('[data-p-note]', 'class')).includes('bad'));
await page.screenshot({ path: `${out}/21-out-preview-insufficient.png` });
await Promise.all([page.waitForNavigation(), page.click('#main form[data-once] button[type=submit]')]);
const err = await page.textContent('.alert-error');
ok('服务端拒绝并显示“库存不足”: ' + err.trim().slice(0, 40), err.includes('库存不足'));
await page.screenshot({ path: `${out}/22-out-rejected.png` });
await page.goto(B + '/stock/out/new?productId=8');
ok('拒绝后库存不变（' + stockBefore + '）', (await page.textContent('[data-p-stock]')) === stockBefore);
// 3. 入库成功 Toast
await page.goto(B + '/stock/in/new?productId=8');
await page.fill('#quantity', '5'); await page.fill('#remark', '前端验收入库');
await Promise.all([page.waitForNavigation(), page.click('#main form[data-once] button[type=submit]')]);
ok('入库成功显示 Toast', await page.isVisible('.toast'));
await page.screenshot({ path: `${out}/23-in-success-toast.png` });
await page.waitForTimeout(4600);
ok('Toast 4 秒后自动消失', (await page.locator('.toast').count()) === 0);
// 4. 图表对数刻度切换
await page.goto(B + '/'); await page.click('[data-scale=log]'); await page.waitForTimeout(200);
ok('趋势图切换到对数刻度', (await page.getAttribute('[data-scale=log]', 'aria-pressed')) === 'true');
await page.screenshot({ path: `${out}/24-dashboard-log-scale.png` });
await ctx.close();
// 5. 移动端抽屉导航
({ ctx, page } = await login('admin', 'admin123', { width: 390, height: 844 }));
await page.click('[data-nav-toggle]'); await page.waitForTimeout(350);
ok('移动端点击菜单按钮打开侧栏抽屉', await page.evaluate(() => document.body.classList.contains('nav-open')));
await page.screenshot({ path: `${out}/25-mobile-nav-open.png` });
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
ok('移动端无横向溢出（scrollWidth-innerWidth=' + overflow + '）', overflow <= 0);
await ctx.close();
// 6. 操作员访问用户管理 → 403 页面
({ ctx, page } = await login('operator', 'operator123'));
const resp = await page.goto(B + '/users');
ok('操作员访问 /users 返回 403 页面（HTTP ' + resp.status() + '）', resp.status() === 403 && (await page.textContent('h2')).includes('没有访问权限'));
ok('操作员侧栏无“用户管理”', (await page.locator('.nav a', { hasText: '用户管理' }).count()) === 0);
await ctx.close();
await browser.close();
console.log(results.join('\n'));
console.log(results.every(r => r.startsWith('✔')) ? '结果：交互验收全部通过' : '结果：存在失败项');
