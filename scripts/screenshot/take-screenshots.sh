#!/usr/bin/env bash
# 用 Chrome 无头模式 + DevTools 协议（Java 自带 WebSocket 客户端，无需 Selenium）给各页面截图
# 用法：scripts/screenshot/take-screenshots.sh [BASE_URL]   截图输出到 docs/screenshots/
set -e
cd "$(dirname "$0")"
B=${1:-http://127.0.0.1:8080}; OUT=../../docs/screenshots; mkdir -p $OUT
google-chrome --headless=new --no-sandbox --disable-gpu --remote-debugging-port=9333 --user-data-dir=/tmp/shot-profile about:blank >/dev/null 2>&1 &
CHROME=$!; sleep 4
login() { rm -f /tmp/shot-cj; local t; t=$(curl -s -c /tmp/shot-cj $B/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"//')
  curl -s -b /tmp/shot-cj -c /tmp/shot-cj -o /dev/null -d "username=$1&password=$2&_csrf=$t" $B/login; grep JSESSIONID /tmp/shot-cj | awk '{print $7}'; }
tab() { curl -s -X PUT "http://127.0.0.1:9333/json/new?about:blank" | python3 -c "import json,sys;print(json.load(sys.stdin)['webSocketDebuggerUrl'])"; }
java Shot.java "$(tab)" invalid $OUT 00-login=$B/login
java Shot.java "$(tab)" "$(login admin admin123)" $OUT 01-dashboard=$B/ 02-products=$B/products 04-product-form=$B/products/new \
  05-categories=$B/categories 06-suppliers=$B/suppliers 07-stock-in=$B/stock/in 08-stock-out-form=$B/stock/out/new?productId=8 \
  09-stock-out-list=$B/stock/out 10-flows=$B/flows 11-consistency=$B/consistency 12-users=$B/users
java Shot.java "$(tab)" "$(login operator operator123)" $OUT 13-operator-403=$B/users 14-operator-products=$B/products
kill $CHROME
