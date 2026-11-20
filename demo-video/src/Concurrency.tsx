import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { C, FONT, MONO } from './theme';
import data from './data.json';

type Cue = { text: string; start: number; duration: number };
const nf = new Intl.NumberFormat('zh-CN');

/** 点阵：每个点代表一个并发请求；绿色=扣减成功，灰色=库存不足被拒，斜纹=重复提交未执行 */
const Dots: React.FC<{ total: number; ok: number; cols: number; size: number; gap: number; t: number; dupMode?: boolean }> = ({ total, ok, cols, size, gap, t, dupMode }) => {
  const shown = Math.floor(interpolate(t, [0, 1], [0, total], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }));
  return (
    <div style={{ display: 'grid', gridTemplateColumns: `repeat(${cols}, ${size}px)`, gap }}>
      {Array.from({ length: total }).map((_, i) => {
        const vis = i < shown; const isOk = i < ok;
        const bg = !vis ? 'rgba(27,37,40,.06)' : isOk ? C.inC : dupMode ? `repeating-linear-gradient(-45deg, #b9cfd1 0 3px, #e3eded 3px 6px)` : '#c3cbcc';
        return <div key={i} style={{ width: size, height: size, borderRadius: size > 14 ? 5 : 2, background: bg }} />;
      })}
    </div>
  );
};

const Stat: React.FC<{ v: number | string; l: string; color?: string }> = ({ v, l, color }) => (
  <div style={{ minWidth: 0 }}>
    <div style={{ fontSize: 42, fontWeight: 700, color: color ?? C.ink, lineHeight: 1.1 }}>{typeof v === 'number' ? nf.format(v) : v}</div>
    <div style={{ fontSize: 21, color: C.ink3, marginTop: 4 }}>{l}</div>
  </div>
);

const Panel: React.FC<{ title: string; sub: string; at: number; active: boolean; raw?: string; children: React.ReactNode }> = ({ title, sub, at, active, raw, children }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const p = spring({ frame: f - at * fps, fps, config: { damping: 17 } });
  return (
    <div style={{ width: 405, height: 590, overflow: 'hidden', background: C.paper, borderRadius: 18, padding: '26px 28px', boxSizing: 'border-box', position: 'relative',
      opacity: p * (active ? 1 : 0.9), transform: `translateY(${(1 - p) * 40}px)`, display: 'flex', flexDirection: 'column', gap: 18,
      boxShadow: active ? '0 18px 50px rgba(0,0,0,.35)' : '0 6px 20px rgba(0,0,0,.18)', borderTop: `8px solid ${active ? C.signal : C.line}` }}>
      <div>
        <div style={{ fontSize: 31, fontWeight: 700, color: C.ink }}>{title}</div>
        <div style={{ fontSize: 21, color: C.ink3, marginTop: 4 }}>{sub}</div>
      </div>
      {children}
      {raw && <div style={{ marginTop: 'auto', fontFamily: MONO, fontSize: 12.5, lineHeight: 1.45, color: C.ink2, background: '#f2f5f4', borderRadius: 8, padding: '8px 10px', wordBreak: 'break-all' }}>{raw}</div>}
    </div>
  );
};

export const Concurrency: React.FC<{ cues: Cue[] }> = ({ cues }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const sec = f / fps;
  const at = cues.map((c) => c.start);
  const activeIdx = at.reduce((acc, s, i) => (sec >= s ? i : acc), 0);
  const prog = (i: number, len = 2.2) => interpolate(sec, [at[i] + 0.3, at[i] + 0.3 + len], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  const { c01, c02, c04, exp, loadout } = data;
  const short = (raw: string) => raw.replace(/ \| 耗时=\d+ms/, '');
  const head = spring({ frame: f, fps, config: { damping: 18 } });
  const jm = spring({ frame: f - (at[5] + 0.2) * fps, fps, config: { damping: 17 } });
  return (
    <AbsoluteFill style={{ background: C.steel, fontFamily: FONT }}>
      <AbsoluteFill style={{ backgroundImage: 'linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.035) 1px, transparent 1px)', backgroundSize: '64px 64px' }} />
      <div style={{ position: 'absolute', left: 117, top: 40, opacity: head, color: '#fff' }}>
        <div style={{ fontSize: 54, fontWeight: 700 }}>高并发防超卖：真实测试结果</div>
        <div style={{ fontSize: 23, color: '#a9b6b9', marginTop: 6 }}>
          数据来源：ConcurrentStockTest / NaiveDeductionExperimentTest（真实 MySQL 8.4，本次 mvn test 输出）与 JMeter 5.6.3 压测 perf/results
        </div>
      </div>
      <div style={{ position: 'absolute', left: 117, top: 172, display: 'flex', gap: 22 }}>
        <Panel title={`${c01.threads} 线程抢 ${c01.stockInit} 件`} sub="每个请求出库 1 件，同时发起" at={at[1]} active={activeIdx === 1} raw={short(c01.raw)}>
          <Dots total={c01.threads} ok={c01.success} cols={10} size={20} gap={4} t={prog(1)} />
          <div style={{ display: 'flex', gap: 22 }}><Stat v={c01.success} l="成功" color={C.inC} /><Stat v={c01.rejected} l="库存不足拒绝" /><Stat v={c01.finalStock} l="最终库存" /></div>
        </Panel>
        <Panel title={`${c02.threads} 线程抢 ${c02.stockInit} 件`} sub="并发再翻 5 倍" at={at[2]} active={activeIdx === 2} raw={short(c02.raw)}>
          <Dots total={c02.threads} ok={c02.success} cols={25} size={10} gap={3} t={prog(2)} />
          <div style={{ display: 'flex', gap: 22 }}><Stat v={c02.success} l="成功" color={C.inC} /><Stat v={c02.rejected} l="库存不足拒绝" /><Stat v={c02.finalStock} l="最终库存" /></div>
        </Panel>
        <Panel title={`同一幂等号 × ${c04.threads}`} sub="同一 requestId 并发重复提交" at={at[3]} active={activeIdx === 3} raw={short(c04.raw)}>
          <Dots total={c04.threads} ok={c04.success} cols={10} size={20} gap={4} t={prog(3, 1.6)} dupMode />
          <div style={{ display: 'flex', gap: 22 }}><Stat v={c04.success} l="实际生效" color={C.inC} /><Stat v={c04.duplicate} l="判定重复" /><Stat v={c04.outOrders} l="出库单数" /></div>
        </Panel>
        <Panel title="反例：先读后写" sub="SELECT → 计算 → UPDATE 写回" at={at[4]} active={activeIdx === 4} raw={exp.raw}>
          {[{ l: '线程认为卖出', v: exp.sold, c: C.outC }, { l: '库存实际减少', v: exp.reduced, c: C.danger }].map((b, i) => {
            const w = interpolate(prog(4, 1.8), [0, 1], [0, (b.v / exp.sold) * 100]);
            return <div key={b.l}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 23, color: C.ink2 }}><span>{b.l}</span><b style={{ color: b.c, fontSize: 30 }}>{b.v} 件</b></div>
              <div style={{ height: 26, background: '#eef1f1', borderRadius: 6, marginTop: 8, overflow: 'hidden' }}>
                <div style={{ width: `${Math.max(w, i === 1 && w > 0 ? 1.5 : 0)}%`, height: '100%', background: b.c, borderRadius: 6 }} /></div>
            </div>;
          })}
          <div style={{ fontSize: 23, lineHeight: 1.5, color: C.danger, background: '#fdecea', borderRadius: 10, padding: '12px 14px' }}>
            丢失更新：{exp.sold} 次“卖出”只扣了 {exp.reduced} 件，最终库存 {exp.finalStock}
          </div>
        </Panel>
      </div>
      <div style={{ position: 'absolute', left: 117, right: 117, top: 782, height: 88, borderRadius: 16, background: 'rgba(255,255,255,.07)', border: '1px solid rgba(255,255,255,.12)',
        display: 'flex', alignItems: 'center', padding: '0 36px', gap: 56, color: '#fff', opacity: jm, transform: `translateY(${(1 - jm) * 30}px)` }}>
        <div style={{ fontSize: 24, color: '#a9b6b9', width: 330, lineHeight: 1.35 }}>JMeter 持续出库<br />{loadout.threads} 线程 × {loadout.loops} 次</div>
        {[[`${loadout.tps}`, 'req/s 吞吐量'], [`${loadout.p95} ms`, 'P95 响应时间'], [nf.format(loadout.samples), '请求数'], [`${loadout.errPct}%`, '系统错误率']].map(([v, l]) => (
          <div key={l} style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
            <span style={{ fontSize: 46, fontWeight: 700, color: C.signal }}>{v}</span><span style={{ fontSize: 22, color: '#c9d3d6' }}>{l}</span></div>
        ))}
      </div>
    </AbsoluteFill>
  );
};
