import React from 'react';
import { AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from 'remotion';
import timeline from './timeline.json';
import { C, FONT } from './theme';
import { Intro, Summary } from './Titles';
import { Concurrency } from './Concurrency';

type Cue = { text: string; start: number; duration: number; audio: string };
type Scene = { id: string; kind: string; chapter: string; start: number; duration: number; cues: Cue[] };
const scenes = timeline.scenes as Scene[];

/** 字幕：烧录在画面底部居中，深色半透明底，与旁白音频同一时间轴 */
const Subtitle: React.FC<{ text: string; dur: number }> = ({ text, dur }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const o = interpolate(f, [0, 5, dur * fps - 5, dur * fps], [0, 1, 1, 0], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-end', alignItems: 'center', paddingBottom: 46, opacity: o }}>
      <div style={{ maxWidth: 1240, padding: '12px 30px', borderRadius: 14, background: 'rgba(15, 24, 29, 0.82)', color: '#fff',
        fontFamily: FONT, fontSize: 42, fontWeight: 500, lineHeight: 1.45, textAlign: 'center', letterSpacing: 1, textWrap: 'balance',
        boxShadow: '0 6px 24px rgba(0,0,0,.25)' }}>{text}</div>
    </AbsoluteFill>
  );
};

/** 章节标签：每个录屏场景开头从左侧滑入，2.8 秒后收起 */
const ChapterTag: React.FC<{ index: number; title: string }> = ({ index, title }) => {
  const f = useCurrentFrame(); const { fps } = useVideoConfig();
  const inn = spring({ frame: f, fps, config: { damping: 18 } });
  const out = interpolate(f, [2.6 * fps, 3.1 * fps], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
  const x = interpolate(inn - out, [0, 1], [-520, 0]);
  return (
    <div style={{ position: 'absolute', left: 0, top: 120, transform: `translateX(${x}px)`, display: 'flex', alignItems: 'stretch',
      fontFamily: FONT, boxShadow: '0 10px 30px rgba(0,0,0,.25)' }}>
      <div style={{ width: 12, background: C.signal }} />
      <div style={{ background: C.steel, color: '#fff', padding: '16px 30px 16px 22px', display: 'flex', alignItems: 'baseline', gap: 16 }}>
        <span style={{ color: C.signal, fontSize: 30, fontWeight: 700 }}>{String(index).padStart(2, '0')}</span>
        <span style={{ fontSize: 34, fontWeight: 600 }}>{title}</span>
      </div>
    </div>
  );
};

/** 场景容器：前 10 帧淡入，避免硬切 */
const Fade: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const f = useCurrentFrame();
  return <AbsoluteFill style={{ opacity: interpolate(f, [0, 10], [0, 1], { extrapolateRight: 'clamp' }) }}>{children}</AbsoluteFill>;
};

export const Demo: React.FC = () => {
  const { fps } = useVideoConfig();
  let chapterNo = 0;
  return (
    <AbsoluteFill style={{ background: C.steel }}>
      {scenes.map((s) => {
        const from = Math.round(s.start * fps); const len = Math.round((s.start + s.duration) * fps) - from;
        const idx = ++chapterNo;
        return (
          <Sequence key={s.id} from={from} durationInFrames={len} name={s.id}>
            <Fade>
              {s.kind === 'rec' && <OffthreadVideo src={staticFile(`clips/${s.id}.mp4`)} muted />}
              {s.id === 'intro' && <Intro cues={s.cues} />}
              {s.id === 'concurrency' && <Concurrency cues={s.cues} />}
              {s.id === 'summary' && <Summary cues={s.cues} />}
            </Fade>
            {s.kind === 'rec' && <ChapterTag index={idx} title={s.chapter} />}
            {s.cues.map((c, i) => (
              <React.Fragment key={i}>
                <Sequence from={Math.round(c.start * fps)} durationInFrames={Math.round(c.duration * fps) + 6} name={`voice-${i}`}>
                  <Audio src={staticFile(c.audio)} />
                </Sequence>
                <Sequence from={Math.round(c.start * fps)} durationInFrames={Math.round(c.duration * fps)} name={`sub-${i}`}>
                  <Subtitle text={c.text} dur={c.duration} />
                </Sequence>
              </React.Fragment>
            ))}
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
