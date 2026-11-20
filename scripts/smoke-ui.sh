#!/usr/bin/env bash
# =====================================================================
# 业务冒烟测试：用 curl 模拟浏览器（登录会话 + CSRF）完整走一遍核心业务表单
# 新增分类/供应商/商品 → 入库 → 出库 → 库存不足 → 重复提交 → 删除限制 → 一致性核对
# 用法：scripts/smoke-ui.sh [BASE_URL]
# =====================================================================
BASE=${1:-http://127.0.0.1:8080}
CJ=$(mktemp); FAIL=0; TAG=$(date +%H%M%S)
csrf() { curl -s -b "$CJ" -c "$CJ" "$BASE$1" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'; }
ok() { if [[ "$2" == *"$3"* ]]; then echo "  ✔ $1"; else echo "  ✘ $1（期望包含：$3）"; FAIL=$((FAIL+1)); fi; }
post() { local path=$1; shift; curl -s -b "$CJ" -c "$CJ" -o /tmp/smoke-body.html -w "%{http_code} %{redirect_url}" "$@" "$BASE$path"; }

T=$(csrf /login); post /login -d "username=admin&password=admin123&_csrf=$T" >/dev/null
echo "[1] 新增分类"
T=$(csrf /categories/new); r=$(post /categories/save --data-urlencode "name=冒烟分类$TAG" -d "description=smoke&_csrf=$T")
ok "保存分类后重定向到列表" "$r" "/categories"
CID=$(curl -s -b "$CJ" "$BASE/categories" | grep -o "/products?categoryId=[0-9]*\">冒烟分类$TAG" | grep -o '[0-9]*' | head -1)
echo "    分类 id=$CID"
echo "[2] 新增供应商（含邮箱格式校验）"
T=$(csrf /suppliers/new); r=$(post /suppliers/save --data-urlencode "name=冒烟供应商$TAG" -d "email=bad-email&_csrf=$T")
ok "非法邮箱被拒绝并留在表单" "$r$(cat /tmp/smoke-body.html)" "邮箱格式不正确"
T=$(csrf /suppliers/new); r=$(post /suppliers/save --data-urlencode "name=冒烟供应商$TAG" -d "email=s@x.com&phone=0516-1234567&_csrf=$T")
ok "合法供应商保存成功" "$r" "/suppliers"
echo "[3] 新增商品"
T=$(csrf /products/new); r=$(post /products/save -d "code=SMOKE$TAG&categoryId=$CID&unit=件&price=12.50&warningThreshold=3&status=1&_csrf=$T" --data-urlencode "name=冒烟商品$TAG")
ok "商品保存成功" "$r" "/products"
T=$(csrf /products/new); post /products/save -d "code=SMOKE$TAG&categoryId=$CID&unit=件&price=1&warningThreshold=3&status=1&_csrf=$T" --data-urlencode "name=重复编码" >/dev/null
ok "重复编码被拒绝" "$(cat /tmp/smoke-body.html)" "已存在"
PID=$(curl -s -b "$CJ" "$BASE/api/products" | python3 -c "import json,sys;print([p['id'] for p in json.load(sys.stdin)['data'] if p['code']=='SMOKE$TAG'][0])")
echo "    商品 id=$PID"
echo "[4] 入库 10 件"
F=$(curl -s -b "$CJ" -c "$CJ" "$BASE/stock/in/new?productId=$PID"); T=$(echo "$F" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
RID=$(echo "$F" | grep -o 'name="requestId" value="[^"]*"' | sed 's/.*value="//;s/"//')
r=$(post /stock/in -d "productId=$PID&quantity=10&unitPrice=8&requestId=$RID&_csrf=$T")
ok "入库成功重定向" "$r" "/stock/in"
echo "[5] 出库 3 件，并用同一 requestId 重复提交"
F=$(curl -s -b "$CJ" -c "$CJ" "$BASE/stock/out/new?productId=$PID"); T=$(echo "$F" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
RID=$(echo "$F" | grep -o 'name="requestId" value="[^"]*"' | sed 's/.*value="//;s/"//')
post /stock/out -d "productId=$PID&quantity=3&requestId=$RID&_csrf=$T" >/dev/null
post /stock/out -d "productId=$PID&quantity=3&requestId=$RID&_csrf=$T" >/dev/null
L=$(curl -s -b "$CJ" "$BASE/stock/out")
ok "重复提交提示" "$L" "重复提交"
S=$(curl -s -b "$CJ" "$BASE/api/products/$PID" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['stock'])")
ok "库存 = 10 - 3 = 7（重复提交未再次扣减）" "stock=$S" "stock=7"
echo "[6] 出库 100 件（库存不足）"
F=$(curl -s -b "$CJ" -c "$CJ" "$BASE/stock/out/new?productId=$PID"); T=$(echo "$F" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
RID=$(echo "$F" | grep -o 'name="requestId" value="[^"]*"' | sed 's/.*value="//;s/"//')
post /stock/out -d "productId=$PID&quantity=100&requestId=$RID&_csrf=$T" >/dev/null
ok "库存不足提示" "$(cat /tmp/smoke-body.html)" "库存不足"
S=$(curl -s -b "$CJ" "$BASE/api/products/$PID" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['stock'])")
ok "库存仍为 7" "stock=$S" "stock=7"
echo "[7] 删除限制"
T=$(csrf /categories); post /categories/$CID/delete -d "_csrf=$T" >/dev/null
ok "分类下有商品不能删除" "$(curl -s -b "$CJ" "$BASE/categories")" "还有商品"
T=$(csrf /products); post /products/$PID/delete -d "_csrf=$T" >/dev/null
ok "有流水的商品不能删除" "$(curl -s -b "$CJ" "$BASE/products")" "已有库存流水"
echo "[8] 一致性核对"
C=$(curl -s -b "$CJ" "$BASE/api/stock/consistency?productId=$PID")
ok "商品一致性 consistent=true" "$C" '"consistent":true'
ok "全量核对页显示全部一致" "$(curl -s -b "$CJ" "$BASE/consistency")" "核对一致"
rm -f "$CJ"
[[ $FAIL -eq 0 ]] && echo "结果：冒烟测试全部通过" || echo "结果：$FAIL 项失败"
exit $FAIL
