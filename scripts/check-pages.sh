#!/usr/bin/env bash
# =====================================================================
# 页面巡检：用 curl 携带登录会话请求所有页面，检查 HTTP 200 且页面无模板错误
# 用法：scripts/check-pages.sh [BASE_URL]   默认 http://127.0.0.1:8080
# =====================================================================
BASE=${1:-${BASE_URL:-http://127.0.0.1:8080}}
WORK=$(mktemp -d)
FAIL=0

login() { # $1 用户名 $2 密码 $3 cookie 文件
  local token
  token=$(curl -s -c "$3" "$BASE/login" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
  curl -s -b "$3" -c "$3" -o /dev/null -w "%{http_code} -> %{redirect_url}" \
       -d "username=$1&password=$2&_csrf=$token" "$BASE/login"
}

check() { # $1 cookie $2 path $3 期望状态码 $4 角色说明
  local code body
  body="$WORK/body.html"
  code=$(curl -s -b "$1" -o "$body" -w "%{http_code}" "$BASE$2")
  local bad=""
  if grep -qiE "TemplateInputException|TemplateProcessingException|SpelEvaluationException|Whitelabel Error|Exception evaluating" "$body"; then
    bad=" [模板错误]"
  fi
  if [[ "$code" == "$3" && -z "$bad" ]]; then
    printf "  ✔ %-4s %-9s %-32s\n" "$code" "$4" "$2"
  else
    printf "  ✘ %-4s %-9s %-32s (期望 %s)%s\n" "$code" "$4" "$2" "$3" "$bad"; FAIL=$((FAIL+1))
  fi
}

echo "目标：$BASE"
echo "[未登录] 访问首页应跳转登录（302）"
code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/")
[[ "$code" == "302" ]] && echo "  ✔ 302 未登录 /" || { echo "  ✘ $code 未登录 /"; FAIL=$((FAIL+1)); }
code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/login")
[[ "$code" == "200" ]] && echo "  ✔ 200 未登录 /login" || { echo "  ✘ $code /login"; FAIL=$((FAIL+1)); }

ADMIN="$WORK/admin.txt"; OPER="$WORK/oper.txt"
echo "[登录] admin: $(login admin admin123 "$ADMIN")"
echo "[登录] operator: $(login operator operator123 "$OPER")"

PID=$(curl -s -b "$ADMIN" "$BASE/api/products" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
CID=1; SID=1; UID_=2
PAGES=( "/" "/products" "/products?keyword=P&page=0" "/products/$PID" "/products/new" "/products/$PID/edit"
        "/categories" "/categories/new" "/categories/$CID/edit"
        "/suppliers" "/suppliers/new" "/suppliers/$SID/edit"
        "/stock/in" "/stock/in/new" "/stock/out" "/stock/out/new" "/stock/out/new?productId=$PID"
        "/flows" "/flows?type=OUT" "/consistency" "/users" "/users/new" "/users/$UID_/edit" "/403" )
echo "[管理员 admin] 全部页面应返回 200"
for p in "${PAGES[@]}"; do check "$ADMIN" "$p" 200 admin; done

echo "[操作员 operator] 业务页面 200，管理页面 403"
for p in "/" "/products" "/products/$PID" "/categories" "/suppliers" "/stock/in" "/stock/in/new" "/stock/out" "/stock/out/new" "/flows" "/consistency"; do
  check "$OPER" "$p" 200 operator
done
for p in "/users" "/users/new" "/products/new" "/products/$PID/edit" "/categories/new" "/suppliers/new"; do
  check "$OPER" "$p" 403 operator
done

echo "[静态资源]"
check "$ADMIN" "/css/app.css" 200 static
check "$ADMIN" "/js/app.js" 200 static

rm -rf "$WORK"
if [[ $FAIL -eq 0 ]]; then echo "结果：全部通过"; else echo "结果：$FAIL 项失败"; fi
exit $FAIL
