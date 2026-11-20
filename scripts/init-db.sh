#!/usr/bin/env bash
# 初始化 MySQL：创建数据库 inventory_db（运行）与 inventory_test（测试）及应用账号。
# 用法（必须先设置口令，不要使用默认占位符部署）：
#   MYSQL_ROOT_PASSWORD=<root密码> DB_USER=inventory DB_PASSWORD=<应用账号密码> scripts/init-db.sh
# 表结构与初始数据无需手工导入：应用启动时自动执行 src/main/resources/db/schema.sql 与 data.sql。
set -euo pipefail
ROOT_PW=${MYSQL_ROOT_PASSWORD:-change_me}
APP_USER=${DB_USER:-inventory}
APP_PW=${DB_PASSWORD:-change_me}
if [[ -z "$ROOT_PW" || "$ROOT_PW" == "change_me" ]]; then
  echo "请设置 MYSQL_ROOT_PASSWORD 后再执行（不要使用占位符 change_me）。" >&2
  exit 1
fi
if [[ -z "$APP_PW" || "$APP_PW" == "change_me" ]]; then
  echo "请设置 DB_PASSWORD 后再执行（不要使用占位符 change_me）。" >&2
  exit 1
fi
mysql -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" -uroot -p"$ROOT_PW" <<SQL
CREATE DATABASE IF NOT EXISTS inventory_db   DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inventory_test DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${APP_USER}'@'localhost' IDENTIFIED BY '${APP_PW}';
CREATE USER IF NOT EXISTS '${APP_USER}'@'127.0.0.1' IDENTIFIED BY '${APP_PW}';
ALTER USER '${APP_USER}'@'localhost' IDENTIFIED BY '${APP_PW}';
ALTER USER '${APP_USER}'@'127.0.0.1' IDENTIFIED BY '${APP_PW}';
GRANT ALL ON inventory_db.*   TO '${APP_USER}'@'localhost';
GRANT ALL ON inventory_test.* TO '${APP_USER}'@'localhost';
GRANT ALL ON inventory_db.*   TO '${APP_USER}'@'127.0.0.1';
GRANT ALL ON inventory_test.* TO '${APP_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
echo "数据库与账号已就绪"
