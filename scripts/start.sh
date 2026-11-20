#!/usr/bin/env bash
# 后台启动应用（nohup），日志写入项目根目录 app.log，PID 写入 app.pid
# 用法：scripts/start.sh [端口]   默认 8080；可用环境变量 DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD 覆盖数据库配置
cd "$(dirname "$0")/.."
PORT=${1:-${PORT:-8080}}
JAR=target/inventory-management-1.0.0.jar
[[ -f $JAR ]] || mvn -B -q -DskipTests package
if ss -ltn 2>/dev/null | grep -q ":$PORT "; then echo "端口 $PORT 已被占用，请换一个端口：scripts/start.sh 8081"; exit 1; fi
# 保留历史日志：已有的 app.log 先归档到 logs/，避免重启后丢失上一次运行（例如压测期间）的日志
if [[ -s app.log ]]; then mkdir -p logs; mv app.log "logs/app-$(date +%Y%m%d-%H%M%S).log"; fi
PORT=$PORT nohup java -Xms256m -Xmx768m -Duser.timezone=Asia/Shanghai -jar "$JAR" > app.log 2>&1 &
echo $! > app.pid
echo "启动中… PID=$(cat app.pid)，端口=$PORT，日志：$(pwd)/app.log"
for i in $(seq 1 60); do
  if grep -q "Started InventoryApplication" app.log; then echo "启动成功：http://127.0.0.1:$PORT"; exit 0; fi
  if ! kill -0 "$(cat app.pid)" 2>/dev/null; then echo "启动失败，请查看 app.log"; exit 1; fi
  sleep 1
done
echo "60 秒内未检测到启动完成，请查看 app.log"
