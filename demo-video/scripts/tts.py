# 用 edge-tts 为每条字幕单独合成语音，测量真实时长，生成时间轴 timeline.json 与 demo.srt
import asyncio, json, re, subprocess, os, sys
import edge_tts
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
nar = json.load(open(f'{ROOT}/narration.json'))
data = json.load(open(f'{ROOT}/src/data.json'))
def fill(t):
    def rep(m):
        v = data
        for k in m.group(1).split('.'): v = v[k]
        if isinstance(v, float) and v.is_integer(): v = int(v)
        return str(v)
    return re.sub(r'\{([\w.]+)\}', rep, t)
os.makedirs(f'{ROOT}/public/audio', exist_ok=True)
def dur(p):
    return float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',p]).decode())
async def synth(text, path):
    if os.path.exists(path) and os.path.getsize(path) > 1000: return
    for attempt in range(4):
        try:
            await edge_tts.Communicate(text, nar['voice'], rate=nar['rate']).save(path); return
        except Exception as e:
            print('retry', attempt, e); await asyncio.sleep(2)
    raise SystemExit('TTS failed: ' + text)
GAP, LEAD, TAIL = 0.25, 0.4, 0.7      # 句间停顿 / 场景开头留白 / 场景结尾留白（秒）
async def main():
    t = 0.0; timeline = []; srt = []; n = 0
    for s in nar['scenes']:
        start = t; cues = []; ct = LEAD
        for i, raw in enumerate(s['cues']):
            # 字幕与朗读可分开：{"text": 字幕原文, "say": 朗读文本}，用于 P95、JMeter 等英文缩写的自然读法
            text = fill(raw['text'] if isinstance(raw, dict) else raw)
            say = fill(raw.get('say', raw['text'])) if isinstance(raw, dict) else text
            path = f'{ROOT}/public/audio/{s["id"]}-{i}.mp3'
            await synth(say, path)
            d = dur(path)
            cues.append({'text': text, 'say': say, 'start': round(ct, 3), 'duration': round(d, 3), 'audio': f'audio/{s["id"]}-{i}.mp3'})
            n += 1
            def ts(x):
                ms = int(round(x * 1000)); return f'{ms//3600000:02d}:{ms//60000%60:02d}:{ms//1000%60:02d},{ms%1000:03d}'
            srt.append(f'{n}\n{ts(start+ct)} --> {ts(start+ct+d)}\n{text}\n')
            ct += d + GAP
        length = ct - GAP + TAIL
        timeline.append({'id': s['id'], 'kind': s['kind'], 'chapter': s['chapter'], 'start': round(start, 3), 'duration': round(length, 3), 'cues': cues})
        t += length
    json.dump({'fps': 30, 'total': round(t, 3), 'voice': nar['voice'], 'scenes': timeline}, open(f'{ROOT}/src/timeline.json', 'w'), ensure_ascii=False, indent=1)
    os.makedirs(f'{ROOT}/out', exist_ok=True)
    open(f'{ROOT}/out/demo.srt', 'w', encoding='utf-8').write('\n'.join(srt))
    print(f'total {t:.1f}s'); [print(f"{x['id']:12s} {x['start']:7.2f} {x['duration']:6.2f}") for x in timeline]
asyncio.run(main())
