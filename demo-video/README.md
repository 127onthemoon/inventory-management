# 演示视频工程

- 成品：`out/demo.mp4`（1920×1080，H.264 + AAC，约 3 分钟），字幕：`out/demo.srt`，抽帧：`out/frames/`
- 旁白文案：`narration.json`；数据：`src/data.json`（由 `scripts/build-data.mjs` 从真实测试结果生成）
- 录屏：`scripts/record.mjs`；Remotion 画面：`src/`；页面截图/交互验收：`scripts/shots.mjs`、`scripts/interact.mjs`
- 一键重做：`npm install && npx remotion browser ensure && npm run all`（需应用在 8080 运行、系统 Chrome、ffmpeg、edge-tts）

详细说明见 `../docs/11-演示视频制作说明.md`。
