// 真实录屏：Playwright 驱动系统 Chrome，CDP Page.startScreencast 抓取 1920×1080 JPEG 帧（按真实时间戳），
// 再用 ffmpeg 合成 30fps H.264 片段 public/clips/<场景>.mp4。各场景操作按 timeline.json 中旁白的时间点执行。
// 用法：node scripts/record.mjs [场景id...]   （不传则录制全部录屏场景）
import { chromium } from 'playwright';
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const B = 'http://127.0.0.1:8080';
const ROOT = new URL('../', import.meta.url).pathname;
const timeline = JSON.parse(fs.readFileSync(ROOT + 'src/timeline.json', 'utf8'));
const only = process.argv.slice(2);
fs.mkdirSync(ROOT + 'public/clips', { recursive: true });

// 演示用“假鼠标”：无头浏览器不绘制光标，这里注入一个跟随真实 mousemove 事件的箭头 + 点击波纹
const CURSOR_JS = `
(() => {
  const mk = () => {
    if (document.getElementById('__cur')) return;
    const c = document.createElement('div'); c.id = '__cur';
    c.innerHTML = '<svg width="28" height="28" viewBox="0 0 24 24"><path d="M4 2l16 9.5-7 1.6-3.6 6.9z" fill="#111" stroke="#fff" stroke-width="1.6" stroke-linejoin="round"/></svg>';
    Object.assign(c.style, {position:'fixed', left:0, top:0, zIndex:2147483647, pointerEvents:'none', transform:'translate(-4px,-2px)', filter:'drop-shadow(0 2px 3px rgba(0,0,0,.35))'});
    document.documentElement.appendChild(c);
    const p = JSON.parse(sessionStorage.getItem('__cur') || '{"x":720,"y":420}');
    c.style.left = p.x + 'px'; c.style.top = p.y + 'px';
    document.addEventListener('mousemove', e => { c.style.left = e.clientX + 'px'; c.style.top = e.clientY + 'px';
      sessionStorage.setItem('__cur', JSON.stringify({x:e.clientX, y:e.clientY})); }, true);
    document.addEventListener('mousedown', e => { const r = document.createElement('div');
      Object.assign(r.style, {position:'fixed', left:(e.clientX-18)+'px', top:(e.clientY-18)+'px', width:'36px', height:'36px', borderRadius:'50%',
        background:'rgba(242,178,0,.45)', zIndex:2147483646, pointerEvents:'none', transition:'transform .45s, opacity .45s'});
      document.documentElement.appendChild(r); requestAnimationFrame(() => { r.style.transform = 'scale(1.8)'; r.style.opacity = '0'; });
      setTimeout(() => r.remove(), 500); }, true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mk); else mk();
})();`;

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox', '--hide-scrollbars'] });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 810 }, deviceScaleFactor: 4 / 3, locale: 'zh-CN' });
await ctx.addInitScript(CURSOR_JS);
const page = await ctx.newPage();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let mouse = { x: 720, y: 420 };
async function moveTo(sel, opt = {}) {
  const loc = typeof sel === 'string' ? page.locator(sel).first() : sel;
  await loc.scrollIntoViewIfNeeded();
  const b = await loc.boundingBox();
  const x = b.x + b.width * (opt.fx ?? 0.5), y = b.y + b.height * (opt.fy ?? 0.5);
  await page.mouse.move(x, y, { steps: opt.steps ?? 22 }); mouse = { x, y };
}
async function moveXY(x, y, steps = 22) { await page.mouse.move(x, y, { steps }); mouse = { x, y }; }
async function click(sel, opt) { await moveTo(sel, opt); await sleep(120); await page.mouse.down(); await sleep(60); await page.mouse.up(); }
async function clickNav(sel, opt) { await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle' }), click(sel, opt)]); await page.mouse.move(mouse.x, mouse.y); }
async function type(sel, text, delay = 70) { await click(sel); await page.keyboard.press('Control+A'); await page.keyboard.type(text, { delay }); }
async function select(sel, value) { await moveTo(sel); await sleep(250); await page.selectOption(sel, value); }
async function login(u, p) {
  await page.goto(B + '/login'); await page.fill('#username', u); await page.fill('#password', p);
  await Promise.all([page.waitForNavigation(), page.click('.login-card button[type=submit]')]);
}

async function record(scene, actions) {
  const cdp = await ctx.newCDPSession(page);
  const frames = [];
  cdp.on('Page.screencastFrame', async (f) => {
    frames.push({ t: f.metadata.timestamp, data: f.data });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }); } catch {}
  });
  await page.mouse.move(mouse.x, mouse.y);
  const t0 = Date.now() / 1000;
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1920, maxHeight: 1080, everyNthFrame: 1 });
  const at = async (sec) => { const w = t0 + sec - Date.now() / 1000; if (w > 0) await sleep(w * 1000); };
  const cue = (i) => scene.cues[i].start;
  await actions(at, cue);
  await at(scene.duration);
  await cdp.send('Page.stopScreencast'); await cdp.detach();
  // 写帧 + concat 列表（每帧显示到下一帧出现为止）
  const dir = `${ROOT}build/frames/${scene.id}`; fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  let list = '';
  frames.forEach((f, i) => {
    const file = `${dir}/${String(i).padStart(5, '0')}.jpg`; fs.writeFileSync(file, Buffer.from(f.data, 'base64'));
    const start = i === 0 ? t0 : f.t; const end = i + 1 < frames.length ? frames[i + 1].t : t0 + scene.duration;
    list += `file '${file}'\nduration ${Math.max(0.001, end - start).toFixed(4)}\n`;
  });
  list += `file '${dir}/${String(frames.length - 1).padStart(5, '0')}.jpg'\n`;
  fs.writeFileSync(`${dir}/list.txt`, list);
  const out = `${ROOT}public/clips/${scene.id}.mp4`;
  execFileSync('ffmpeg', ['-y', '-v', 'error', '-f', 'concat', '-safe', '0', '-i', `${dir}/list.txt`,
    '-vf', 'scale=1920:1080:flags=lanczos,fps=30,format=yuv420p', '-t', String(scene.duration), '-c:v', 'libx264', '-preset', 'medium', '-crf', '16', out]);
  console.log(`录制 ${scene.id}: ${frames.length} 帧, ${scene.duration}s -> ${out}`);
}

const S = Object.fromEntries(timeline.scenes.map((s) => [s.id, s]));
const scenes = {
  async login() {
    await ctx.clearCookies(); await page.goto(B + '/login'); await page.evaluate(() => sessionStorage.clear()); await page.reload();
    await moveXY(900, 560, 5);
    await record(S.login, async (at, cue) => {
      await at(0.6); await moveTo('.login-hero h2', { fx: 0.3, steps: 30 });
      await at(cue(1)); await moveTo('.sql-card', { fx: 0.25, fy: 0.85, steps: 30 });
      await at(cue(1) + 1.6); await moveTo('.sql-card .hl', { steps: 20 });
      await at(cue(2) - 1.4); await type('#username', 'admin', 60); await type('#password', 'admin123', 45);
      await clickNav('.login-card button[type=submit]');
    });
  },
  async dashboard() {
    await record(S.dashboard, async (at, cue) => {
      await at(cue(1)); for (const i of [1, 2, 3, 4]) { await moveTo(`.kpi:nth-child(${i}) .kpi-value`, { fx: 0.2, steps: 18 }); await sleep(900); }
      await at(cue(2)); await moveTo('#trend-chart', { fx: 0.62, fy: 0.55, steps: 25 }); await sleep(700);
      await moveTo('#trend-chart', { fx: 0.85, fy: 0.6, steps: 15 }); await sleep(900);
      await moveTo('#trend-chart', { fx: 0.35, fy: 0.5, steps: 18 }); await sleep(900); await moveTo('#trend-chart', { fx: 0.9, fy: 0.55, steps: 18 });
      await at(cue(3)); await moveTo('.donut', { steps: 25 }); await sleep(900); await moveTo('.is-low .btn', { steps: 20 });
    });
  },
  async master() {
    await record(S.master, async (at, cue) => {
      await at(0.3); await clickNav('.nav a[href="/products"]');
      await at(cue(1)); await moveTo('tbody tr:not(.is-low) .gauge', { steps: 25 }); await sleep(1200); await moveTo('tbody tr:nth-child(5) .gauge', { steps: 18 });
      await at(cue(2)); await moveTo('tbody tr.is-low .gauge', { steps: 20 }); await sleep(900); await moveTo('tbody tr.is-low .badge', { steps: 15 });
      await at(cue(3)); await click('tbody tr:first-child form[data-confirm] button'); await page.waitForSelector('dialog.confirm[open]');
      await sleep(1500); await click('[data-confirm-cancel]');   // 只演示弹窗，点“取消”，不删除任何数据
      await at(cue(4) - 0.3); await clickNav('.nav a[href="/categories"]'); await moveTo('tbody tr:nth-child(2) td.name', { steps: 15 });
      await at(cue(4) + 1.6); await clickNav('.nav a[href="/suppliers"]'); await moveTo('tbody tr:nth-child(3) td.name', { steps: 15 });
    });
  },
  async inbound() {
    await record(S.inbound, async (at, cue) => {
      await at(0.3); await clickNav('.nav a[href="/stock/in"]');
      await sleep(400); await clickNav('.page-head a[href="/stock/in/new"]');
      await select('#productId', '4');
      await at(cue(1) - 0.6); await type('#quantity', '30', 120); await moveTo('[data-preview]', { fy: 0.55, steps: 20 });
      await at(cue(2) - 1.2); await select('#supplierId', '2'); await type('#remark', '中性笔补货', 90);
      await at(cue(2) + 1.2); await clickNav('#main form[data-once] button[type=submit]');
      await moveTo('tbody tr:first-child td:nth-child(7)', { steps: 25 });
    });
  },
  async outbound() {
    await record(S.outbound, async (at, cue) => {
      await at(0.2); await clickNav('.nav a[href="/stock/out"]');
      await sleep(300); await clickNav('.page-head a[href="/stock/out/new"]');
      await select('#productId', '1');
      await at(cue(1)); await type('#quantity', '999', 130); await moveTo('[data-p-note]', { steps: 22 });
      await at(cue(2) - 0.2); await clickNav('#main form[data-once] button[type=submit]');
      await moveTo('.alert-error', { fx: 0.3, steps: 22 }); await sleep(1500); await moveTo('[data-p-stock]', { steps: 18 });
      await at(cue(3) - 0.5); await page.goto(B + '/stock/out/new?productId=1', { waitUntil: 'networkidle' }); await page.mouse.move(mouse.x, mouse.y);
      await type('#quantity', '2', 80); await type('#customer', '徐州门店', 70);
      await clickNav('#main form[data-once] button[type=submit]');
    });
  },
  async flows() {
    await record(S.flows, async (at, cue) => {
      await at(0.2); await clickNav('.nav a[href="/flows"]');
      await moveTo('tbody tr:first-child td:nth-child(4)', { steps: 22 });
      await at(cue(1)); await moveTo('tbody tr:first-child td:nth-child(6)', { steps: 20 }); await sleep(700); await moveTo('tbody tr:first-child td.mono', { steps: 15 });
      await at(cue(2) - 0.2); await clickNav('.nav a[href="/consistency"]'); await moveTo('.alert', { fx: 0.3, steps: 20 }); await sleep(1200);
      await moveTo('tbody tr:nth-child(2) td:last-child', { steps: 22 });
    });
  },
  async permission() {
    await record(S.permission, async (at, cue) => {
      await at(0.2); await clickNav('.user-box button[type=submit]');
      await type('#username', 'operator', 40); await type('#password', 'operator123', 30);
      await clickNav('.login-card button[type=submit]');
      await at(cue(1)); await moveTo('.nav', { fy: 0.85, steps: 25 }); await sleep(800); await moveTo('.user-meta', { steps: 20 });
      await sleep(600); await clickNav('.nav a[href="/products"]'); await moveTo('tbody tr:first-child .row-actions', { steps: 20 });
      await at(cue(2)); await page.goto(B + '/users', { waitUntil: 'networkidle' }); await page.mouse.move(mouse.x, mouse.y);
      await sleep(400); await moveTo('.error-page h2', { steps: 22 }); await sleep(1300); await moveTo('.error-page .actions .btn:last-child', { steps: 18 });
    });
  },
};
// 录制前：管理员登录状态由 login 场景建立；单独录某个后续场景时自动用管理员登录
for (const id of Object.keys(scenes)) {
  if (only.length && !only.includes(id)) continue;
  if (id !== 'login' && only.length && only[0] === id) {
    await login('admin', 'admin123');
    if (id !== 'dashboard') await page.goto(B + '/');
  }
  await scenes[id]();
}
await browser.close();
