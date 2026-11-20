#!/usr/bin/env bash
# =====================================================================
# 重建“干净的演示数据”（仅作用于运行库 inventory_db，绝不触碰测试库 inventory_test）
#   1) 备份当前 inventory_db 到 backup/（mysqldump，gzip；backup/ 不进发布包）
#   2) 清空 7 张业务表，重新执行 src/main/resources/db/data.sql（账号 + 4 分类 + 3 供应商 + 8 商品及期初入库）
#   3) scripts/seed_demo.py：通过管理后台表单新增分类/供应商/商品，通过 REST 接口办理约 240 张出入库单
#   4) 回填单据与流水的日期及单号中的时间部分（只改时间，不改数量），分布在过去 13 天 ~ 今天
#   5) 执行 scripts/check-consistency.sql 核对：库存 = Σ入库 − Σ出库、流水链连续、无负库存、无重复幂等号
# 用法：scripts/seed-demo.sh [BASE_URL]      需要应用已在运行（默认 http://127.0.0.1:8080）
#       NO_BACKUP=1 scripts/seed-demo.sh      跳过第 1 步备份
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f ~/tools/env.sh ]] && source ~/tools/env.sh
BASE=${1:-http://127.0.0.1:8080}
DB=${DB_NAME:-inventory_db}
[[ "$DB" == "inventory_test" ]] && { echo "拒绝在测试库上运行"; exit 1; }
export MYSQL_PWD="${DB_PASSWORD:-change_me}"
MYSQL=(mysql -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" -u"${DB_USER:-inventory}" --default-character-set=utf8mb4)

curl -s -o /dev/null -w "%{http_code}" "$BASE/login" | grep -q 200 || { echo "应用未运行：$BASE（先执行 scripts/start.sh）"; exit 1; }

if [[ -z "${NO_BACKUP:-}" ]]; then
  mkdir -p backup
  F="backup/${DB}-$(date +%Y%m%d-%H%M%S).sql"
  mysqldump -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" -u"${DB_USER:-inventory}" --single-transaction --no-tablespaces \
            --default-character-set=utf8mb4 "$DB" > "$F" && gzip "$F"
  echo "[1] 已备份：$F.gz"
fi

echo "[2] 清空业务表并重新执行 data.sql（库：$DB）"
"${MYSQL[@]}" "$DB" <<SQL
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE stock_flow; TRUNCATE TABLE stock_out_order; TRUNCATE TABLE stock_in_order;
TRUNCATE TABLE product; TRUNCATE TABLE supplier; TRUNCATE TABLE category; TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;
SOURCE src/main/resources/db/data.sql;
SQL

echo "[3] 通过表单与接口生成演示数据"
python3 scripts/seed_demo.py --base "$BASE" --sql-out /tmp/seed-demo-backfill.sql

echo "[4] 回填日期（$(wc -l < /tmp/seed-demo-backfill.sql) 条 UPDATE，只改 created_at 与单号时间部分）"
"${MYSQL[@]}" "$DB" < /tmp/seed-demo-backfill.sql

echo "[5] 一致性核对（scripts/check-consistency.sql）"
"${MYSQL[@]}" -t "$DB" -e "SET @pid = NULL; SOURCE scripts/check-consistency.sql;" | tee /tmp/seed-demo-check.txt
MISMATCH=$(grep -c MISMATCH /tmp/seed-demo-check.txt || true)
EXTRA=$("${MYSQL[@]}" -E "$DB" -e "SET @pid = NULL; SOURCE scripts/check-consistency.sql;" | grep -E "broken_chain_rows|negative_stock_products|dup_out_request_ids|dup_flow_refs" | awk '{s+=$2} END {print s+0}')
"${MYSQL[@]}" "$DB" -e "SELECT DATE(created_at) d, COUNT(*) orders, SUM(quantity) qty FROM stock_in_order GROUP BY d ORDER BY d;
                        SELECT DATE(created_at) d, COUNT(*) orders, SUM(quantity) qty FROM stock_out_order GROUP BY d ORDER BY d;
                        SELECT COUNT(*) products, SUM(stock <= warning_threshold) low_stock, SUM(stock) total_stock FROM product;"
if [[ "$MISMATCH" == "0" && "$EXTRA" == "0" ]]; then echo "结果：演示数据已生成，全部商品核对一致（MISMATCH=0）"; else echo "结果：发现 $MISMATCH 个不一致商品，流水链/负库存/重复项合计 $EXTRA"; exit 1; fi
