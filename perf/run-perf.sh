#!/usr/bin/env bash
# =====================================================================
# JMeter 压测一键脚本（非 GUI 模式）
# 用法：perf/run-perf.sh [场景...]     不带参数则依次执行全部场景
# 场景：warmup burst100 burst500 idempotent load-out load-depletion mixed
# 依赖：jmeter、mysql 客户端、python3、curl；应用已在 $BASE 运行
# 每个场景：①用 SQL 新建一个压测商品 → ②调用入库接口设置初始库存（保证流水一致）
#          ③运行 JMeter → ④分析 .jtl → ⑤SQL 核对数据一致性；原始结果全部保存在 perf/results/
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"
HOST=${HOST:-127.0.0.1}; PORT=${PORT:-8080}; BASE="http://$HOST:$PORT"
DB_HOST=${DB_HOST:-127.0.0.1}; DB_PORT=${DB_PORT:-3306}; DB_NAME=${DB_NAME:-inventory_db}
DB_USER=${DB_USER:-inventory}; DB_PASSWORD=${DB_PASSWORD:-change_me}
OUT=results; mkdir -p "$OUT"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" --default-character-set=utf8mb4)

prepare_product() { # $1 初始库存 → 输出商品 id
  local code="PERF-$(date +%H%M%S)-$RANDOM"
  "${MYSQL[@]}" -N -e "INSERT INTO product(code,name,category_id,supplier_id,unit,price,warning_threshold,status)
                       VALUES('$code','压测商品 $code',1,1,'件',1.00,10,1); SELECT LAST_INSERT_ID();" 2>/dev/null
}

stock_in() { # $1 商品 id  $2 数量
  curl -s -u admin:admin123 -H 'Content-Type: application/json' \
       -d "{\"productId\":$1,\"quantity\":$2,\"requestId\":\"perf-init-$1\",\"remark\":\"压测期初库存\"}" \
       "$BASE/api/stock/in" > /dev/null
}

run() { # $1 场景名 $2 jmx $3 初始库存  其余为 -J 参数
  local name=$1 jmx=$2 init=$3; shift 3
  local pid; pid=$(prepare_product)
  [[ "$init" -gt 0 ]] && stock_in "$pid" "$init"
  echo "================ 场景 $name：商品 id=$pid 初始库存=$init 参数：$* ================"
  rm -rf "$OUT/$name.jtl" "$OUT/$name-report"
  local t0; t0=$(date '+%Y-%m-%d %H:%M:%S')
  jmeter -n -t "$jmx" -Jhost="$HOST" -Jport="$PORT" -JproductId="$pid" "$@" \
         -Jjmeter.save.saveservice.output_format=csv \
         -l "$OUT/$name.jtl" -j "$OUT/$name-jmeter.log" -e -o "$OUT/$name-report" | tee "$OUT/$name-console.txt"
  python3 analyze_jtl.py "$OUT/$name.jtl" "$OUT/$name-summary.json" > /dev/null
  {
    echo "场景：$name  开始时间：$t0  商品 id：$pid  初始库存：$init  JMeter 参数：$*"
    echo "---- SQL 一致性核对（scripts/check-consistency.sql）----"
    "${MYSQL[@]}" -t -e "SET @pid = $pid; SOURCE ../scripts/check-consistency.sql;" 2>/dev/null
    echo "---- 应用一致性接口 /api/stock/consistency?productId=$pid ----"
    curl -s -u admin:admin123 "$BASE/api/stock/consistency?productId=$pid"; echo
  } > "$OUT/$name-db-check.txt"
  cat "$OUT/$name-db-check.txt"
  python3 - "$OUT/$name-summary.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for k, v in d.items():
    print(f"  [{k}] 样本={v['samples']} 吞吐={v['throughput_per_s']}/s 平均={v['avg_ms']}ms P95={v['p95_ms']}ms "
          f"P99={v['p99_ms']}ms 200={v['http_200_or_302']} 409业务拒绝={v['business_reject_409']} "
          f"系统错误={v['system_errors']}({v['system_error_rate_pct']}%)")
PY
}

SCENARIOS=("$@"); [[ ${#SCENARIOS[@]} -eq 0 ]] && SCENARIOS=(warmup burst100 burst500 idempotent load-out load-depletion mixed)
for s in "${SCENARIOS[@]}"; do
  case $s in
    warmup)         run warmup stock-load.jmx 100000 -Jthreads=50 -Jloops=40 -Jrampup=2 -Jqty=1 ;;  # 预热 JIT/连接池，结果单独保存不计入分析
    burst100)       run burst100 stock-out-burst.jmx 50 -Jthreads=100 -Jloops=1 -Jrampup=2 -Jqty=1 ;;
    burst500)       run burst500 stock-out-burst.jmx 50 -Jthreads=500 -Jloops=1 -Jrampup=5 -Jqty=1 ;;
    idempotent)     run idempotent stock-out-burst.jmx 50 -Jthreads=100 -Jloops=1 -Jrampup=2 -Jqty=1 -JidemKey="perf-same-key-$RANDOM" ;;
    load-out)       run load-out stock-load.jmx 100000 -Jthreads=200 -Jloops=50 -Jrampup=5 -Jqty=1 ;;
    load-depletion) run load-depletion stock-load.jmx 5000 -Jthreads=200 -Jloops=50 -Jrampup=5 -Jqty=1 ;;
    mixed)          run mixed stock-load.jmx 1000 -Jthreads=200 -Jloops=50 -Jrampup=5 -Jqty=2 -JinRatio=30 ;;
    *) echo "未知场景 $s"; exit 1 ;;
  esac
done
