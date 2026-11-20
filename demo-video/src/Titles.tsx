import React from 'react';
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import { C, FONT } from './theme';
import data from './data.json';

type Cue = { text: string; start: number; duration: number };
const useAppear = (sec: number, damping = 16) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  return spring({ frame: f - sec * fps, fps, config: { damping } });
};
// Lucide warehouse 图标（ISC 协议），与系统侧栏 Logo 一致
const Warehouse: React.FC<{ size: number; color: string }> = ({ size, color }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 21V10a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1v11" /><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35a2 2 0 0 1 1.26-1.86l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35z" />
    <path d="M6 13h12" /><path d="M6 17h12" />
  </svg>
);
const Check: React.FC<{ size?: number; color?: string }> = ({ size = 34, color = C.signal }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><path d="m9 12 2 2 4-4" />
  </svg>
);
const Hazard: React.FC = () => (
  <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 16,
    background: `repeating-linear-gradient(-45deg, ${C.signal} 0 22px, ${C.steel} 22px 44px)` }} />
);
const Grid: React.FC = () => (
  <AbsoluteFill style={{ backgroundImage: 'linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.035) 1px, transparent 1px)',
    backgroundSize: '64px 64px' }} />
);

export const Intro: React.FC<{ cues: Cue[] }> = ({ cues }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const a0 = useAppear(0.1), shot = useAppear(0.5, 20);
  const stack = ['Java 21', 'Spring Boot 3.5', 'Spring Data JPA', 'MySQL 8.4', 'Thymeleaf', '原生 HTML / CSS / JS'];
  const goals = ['不超卖', '不出现负库存', '不重复扣减'];
  return (
    <AbsoluteFill style={{ background: C.steel, fontFamily: FONT, color: '#fff' }}>
      <Grid />
      <div style={{ position: 'absolute', left: 120, top: 150, width: 860, opacity: a0, transform: `translateY(${(1 - a0) * 30}px)` }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 22 }}>
          <div style={{ width: 84, height: 84, borderRadius: 18, background: C.signal, display: 'grid', placeItems: 'center' }}><Warehouse size={48} color={C.steel} /></div>
          <div style={{ fontSize: 30, color: '#a9b6b9' }}>Java 课程结课项目演示</div>
        </div>
        <div style={{ fontSize: 100, fontWeight: 700, lineHeight: 1.15, marginTop: 40, letterSpacing: 2 }}>高并发<br />库存管理系统</div>
      </div>
      <div style={{ position: 'absolute', left: 120, top: 590, width: 860, display: 'flex', flexWrap: 'wrap', gap: 14 }}>
        {stack.map((s, i) => {
          const p = spring({ frame: f - (cues[1].start + i * 0.32) * fps, fps, config: { damping: 15 } });
          return <div key={s} style={{ opacity: p, transform: `translateY(${(1 - p) * 20}px)`, padding: '10px 22px', borderRadius: 999,
            border: '2px solid rgba(255,255,255,.18)', fontSize: 28, color: '#d6dee0' }}>{s}</div>;
        })}
      </div>
      <div style={{ position: 'absolute', left: 120, top: 805, display: 'flex', gap: 40 }}>
        {goals.map((g, i) => {
          const p = spring({ frame: f - (cues[2].start + 0.6 + i * 0.7) * fps, fps, config: { damping: 14 } });
          return <div key={g} style={{ opacity: p, transform: `scale(${0.85 + p * 0.15})`, display: 'flex', alignItems: 'center', gap: 12, fontSize: 38, fontWeight: 600 }}>
            <Check size={40} />{g}</div>;
        })}
      </div>
      <div style={{ position: 'absolute', right: -60, top: 170, width: 900, height: 620, borderRadius: 18, overflow: 'hidden',
        boxShadow: '0 30px 80px rgba(0,0,0,.5)', border: '1px solid rgba(255,255,255,.12)',
        opacity: shot, transform: `perspective(1600px) rotateY(${-14 + (1 - shot) * -10}deg) translateX(${(1 - shot) * 200}px)` }}>
        <Img src={staticFile('img/dashboard.png')} style={{ width: '100%', objectFit: 'cover', objectPosition: 'top left' }} />
      </div>
      <Hazard />
    </AbsoluteFill>
  );
};

export const Summary: React.FC<{ cues: Cue[] }> = ({ cues }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const pillars = [['数据库原子条件更新', 'UPDATE … WHERE stock >= :qty'], ['事务', '扣库存 + 写单据 + 写流水 同生共死'], ['唯一约束', 'requestId 幂等号，重复提交只执行一次']];
  const guarantees = ['不超卖', '无负库存', '不重复扣减'];
  const t = data.tests, ch = data.checks;
  const stats: [string, string][] = [
    [`${t.total - t.failures - t.errors} / ${t.total}`, 'mvn test 自动化测试通过'],
    [`${ch.checkPages.pass} 项`, '页面巡检 check-pages 通过'],
    [`${ch.smoke.pass} 项`, '业务冒烟 smoke-ui 通过'],
    [`${ch.ui.pass} 项`, '浏览器交互验收通过'],
  ];
  const end = spring({ frame: f - (cues[2].start + cues[2].duration - 1.6) * fps, fps, config: { damping: 18 } });
  return (
    <AbsoluteFill style={{ background: C.steel, fontFamily: FONT, color: '#fff' }}>
      <Grid />
      <div style={{ position: 'absolute', left: 120, top: 90, fontSize: 64, fontWeight: 700 }}>总结</div>
      <div style={{ position: 'absolute', left: 120, top: 210, display: 'flex', gap: 28 }}>
        {pillars.map(([h, s], i) => {
          const p = spring({ frame: f - (cues[0].start + i * 0.8) * fps, fps, config: { damping: 16 } });
          return <div key={h} style={{ width: 540, padding: '28px 32px', borderRadius: 16, background: 'rgba(255,255,255,.06)', borderTop: `6px solid ${C.signal}`,
            opacity: p, transform: `translateY(${(1 - p) * 30}px)` }}>
            <div style={{ fontSize: 40, fontWeight: 700 }}>{h}</div>
            <div style={{ fontSize: 26, color: '#a9b6b9', marginTop: 10 }}>{s}</div>
          </div>;
        })}
      </div>
      <div style={{ position: 'absolute', left: 120, top: 450, display: 'flex', gap: 56 }}>
        {guarantees.map((g, i) => {
          const p = spring({ frame: f - (cues[1].start + 0.2 + i * 0.6) * fps, fps, config: { damping: 14 } });
          return <div key={g} style={{ opacity: p, display: 'flex', alignItems: 'center', gap: 14, fontSize: 52, fontWeight: 700 }}><Check size={52} />{g}</div>;
        })}
      </div>
      <div style={{ position: 'absolute', left: 120, top: 600, display: 'flex', gap: 28 }}>
        {stats.map(([v, l], i) => {
          const p = spring({ frame: f - (cues[2].start + 1.2 + i * 0.4) * fps, fps, config: { damping: 16 } });
          return <div key={l} style={{ width: 400, opacity: p, padding: '22px 28px', borderRadius: 14, border: '2px solid rgba(255,255,255,.14)' }}>
            <div style={{ fontSize: 50, fontWeight: 700, color: C.signal }}>{v}</div>
            <div style={{ fontSize: 24, color: '#c9d3d6', marginTop: 4 }}>{l}</div>
          </div>;
        })}
      </div>
      <AbsoluteFill style={{ background: C.steel, opacity: end * 0.92, justifyContent: 'center', alignItems: 'center' }}>
        <div style={{ textAlign: 'center', transform: `scale(${0.9 + end * 0.1})` }}>
          <div style={{ width: 110, height: 110, margin: '0 auto 30px', borderRadius: 24, background: C.signal, display: 'grid', placeItems: 'center' }}><Warehouse size={64} color={C.steel} /></div>
          <div style={{ fontSize: 88, fontWeight: 700 }}>谢谢观看</div>
          <div style={{ fontSize: 30, color: '#a9b6b9', marginTop: 16 }}>高并发库存管理系统 · Java 课程结课项目</div>
        </div>
      </AbsoluteFill>
      <Hazard />
    </AbsoluteFill>
  );
};
