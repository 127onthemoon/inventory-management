#!/usr/bin/env bash
cd "$(dirname "$0")/.."
if [[ -f app.pid ]] && kill -0 "$(cat app.pid)" 2>/dev/null; then kill "$(cat app.pid)" && echo "已停止 PID=$(cat app.pid)"; rm -f app.pid; else echo "应用未运行"; fi
