// 从真实测试/压测结果文件中提取视频要展示的数据（不手写任何数字）
// 输入：docs/test-results/concurrency-test-output-redesign.txt、docs/test-results/mvn-test-redesign.log、perf/results/*-summary.json
import fs from 'node:fs';
const root = new URL('../../', import.meta.url).pathname;
const conc = fs.readFileSync(root + 'docs/test-results/concurrency-test-output-redesign.txt', 'utf8');
const line = (id) => conc.split('\n').find((l) => l.includes(`[CONCURRENCY] ${id} `));
const num = (l, key) => Number(new RegExp(key + '=(\\d+)').exec(l)[1]);
function tc(id) {
  const l = line(id);
  const m = /(\d+)并发抢(\d+)/.exec(l) || /同一幂等号(\d+)并发/.exec(l);
  return { raw: l.trim(), threads: Number(m[1]), stockInit: m[2] ? Number(m[2]) : undefined,
    success: num(l, '成功'), rejected: num(l, '库存不足拒绝'), duplicate: num(l, '重复提交'), errors: num(l, '其他错误'),
    finalStock: num(l, '最终库存'), outOrders: num(l, '出库单'), chainOk: l.includes('流水链=连续') };
}
const expL = conc.split('\n').find((l) => l.includes('[EXPERIMENT] 错误写法'));
const exp = { raw: expL.trim(), sold: num(expL, '认为卖出'), finalStock: num(expL, '最终库存'), reduced: num(expL, '而库存只减少了') };
const mvn = fs.readFileSync(root + 'docs/test-results/mvn-test-demo-data.log', 'utf8');
const tot = [...mvn.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$/gm)].pop();
const perf = (name, sampler = '出库') => {
  const s = JSON.parse(fs.readFileSync(root + `perf/results/${name}-summary.json`, 'utf8'))[sampler];
  const chk = fs.readFileSync(root + `perf/results/${name}-db-check.txt`, 'utf8');
  const th = /-Jthreads=(\d+) -Jloops=(\d+)/.exec(chk);
  return { threads: Number(th[1]), loops: Number(th[2]), samples: s.samples, tps: s.throughput_per_s, tpsRound: Math.round(s.throughput_per_s), p95: s.p95_ms, ok200: s.http_200_or_302,
    rej409: s.business_reject_409, sysErr: s.system_errors, errPct: s.system_error_rate_pct };
};
const data = {
  c01: tc('TC-C01'), c02: tc('TC-C02'), c04: tc('TC-C04'), exp,
  tests: { total: Number(tot[1]), failures: Number(tot[2]), errors: Number(tot[3]), skipped: Number(tot[4]) },
  burst100: perf('burst100'), burst500: perf('burst500'), idem: perf('idempotent'), loadout: perf('load-out'), depletion: perf('load-depletion'),
  checks: Object.fromEntries([['checkPages', 'check-pages-demo-data.txt'], ['smoke', 'smoke-ui-demo-data.txt'], ['ui', 'ui-interaction-demo-data.txt']].map(([k, f]) => {
    const t = fs.readFileSync(root + 'docs/test-results/' + f, 'utf8');
    return [k, { pass: (t.match(/✔/g) || []).length, fail: (t.match(/✘/g) || []).length }];
  })),
  sources: ['docs/test-results/concurrency-test-output-redesign.txt', 'docs/test-results/mvn-test-demo-data.log', 'perf/results/*-summary.json'],
};
fs.writeFileSync(new URL('../src/data.json', import.meta.url), JSON.stringify(data, null, 2));
console.log(JSON.stringify(data, null, 1).slice(0, 1500));
