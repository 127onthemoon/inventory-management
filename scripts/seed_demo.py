#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
演示数据生成器（由 scripts/seed-demo.sh 调用）。

原则：所有业务数据都通过应用的正常入口产生，不手写流水 ——
  * 分类 / 供应商 / 商品：走管理后台表单（登录会话 + CSRF），与人工录入完全相同；
  * 入库 / 出库：走 REST 接口 POST /api/stock/in、/api/stock/out（HTTP Basic，带幂等号），
    由 StockService 在同一事务里完成「原子改库存 + 写单据 + 写流水」。
唯一的“后期处理”是回填时间：把每张单据及其流水的 created_at（以及单号中的时间部分）改为计划中的日期时间
（过去 13 天 ~ 今天），让仪表盘“近 7 日”图表有分布。回填只改时间、不改任何数量，
且时间顺序与单据 id 顺序一致，所以 库存 = 期初 + Σ入库 − Σ出库、流水链连续 都不受影响。

用法：python3 scripts/seed_demo.py [--dry-run] [--base http://127.0.0.1:8080] [--sql-out 文件]
  --dry-run 只做本地模拟并打印每日汇总，不访问应用、不改数据库。
"""
import argparse, base64, datetime as dt, http.cookiejar, json, random, re, sys, urllib.parse, urllib.request

# ---------------------------------------------------------------- 基础资料（新增部分）
NEW_CATEGORIES = [('劳保用品', '手套、安全帽等个人防护用品'), ('包装耗材', '纸箱、胶带、缓冲材料')]
NEW_SUPPLIERS = [
    # name, contact, phone, email, address, remark
    ('无锡安盾劳保用品厂', '陈工', '0510-82330004', 'service@andun.example.com', '江苏省无锡市惠山区', '劳保用品'),
    ('上海捷运包装材料有限公司', '周经理', '021-57880005', 'pack@jieyun.example.com', '上海市嘉定区', '包装耗材，月结'),
]
# code, name, 分类名, 供应商名(None=无), 单位, 单价, 预警阈值
NEW_PRODUCTS = [
    ('P1003', '机械键盘(87键)',      '电子产品', '徐州华辰电子有限公司', '把', 239.00, 10),
    ('P1004', '27英寸显示器',        '电子产品', '徐州华辰电子有限公司', '台', 1099.00, 5),
    ('P1005', '移动电源 20000mAh',   '电子产品', '徐州华辰电子有限公司', '个', 129.00, 15),
    ('P2003', '订书机(中号)',        '办公用品', '南京文汇办公用品公司', '个', 22.00, 10),
    ('P2004', '文件收纳盒',          '办公用品', '南京文汇办公用品公司', '个', 16.50, 20),
    ('P3003', '速溶咖啡(30条)',      '食品饮料', '苏州优鲜食品有限公司', '盒', 45.00, 15),
    ('P3004', '坚果礼盒',            '食品饮料', '苏州优鲜食品有限公司', '盒', 88.00, 10),
    ('P4002', '洗手液 500ml',        '日用百货', None,                   '瓶', 19.90, 20),
    ('P5001', '防护手套(10副)',      '劳保用品', '无锡安盾劳保用品厂',   '包', 32.00, 20),
    ('P5002', '安全帽',              '劳保用品', '无锡安盾劳保用品厂',   '顶', 45.00, 10),
    ('P6001', '瓦楞纸箱(中号)',      '包装耗材', '上海捷运包装材料有限公司', '个', 3.20, 60),
    ('P6002', '封箱胶带(6卷)',       '包装耗材', '上海捷运包装材料有限公司', '组', 18.00, 30),
]
# 模拟参数：code -> (首批入库量 / None 表示沿用 data.sql 期初, 单次出库量范围, 每天出库概率, 补货量, 最终是否保持低库存)
SIM = {
    'P1001': (None, (3, 12), 0.75, 80, False),  'P1002': (None, (10, 30), 0.85, 200, False),
    'P2001': (None, (5, 15), 0.70, 60, False),  'P2002': (None, (1, 3), 0.50, 30, True),
    'P3001': (None, (4, 12), 0.75, 50, False),  'P3002': (None, (1, 2), 0.40, 30, True),
    'P4001': (None, (3, 8), 0.65, 40, False),   'P9001': (None, (0, 0), 0.0, 0, False),   # 秒杀演示商品保持 50 件不动
    'P1003': (40, (2, 6), 0.60, 30, False),     'P1004': (12, (1, 3), 0.45, 10, True),
    'P1005': (60, (3, 9), 0.70, 50, False),     'P2003': (30, (1, 4), 0.50, 20, False),
    'P2004': (80, (4, 12), 0.65, 60, False),    'P3003': (50, (3, 8), 0.70, 40, False),
    'P3004': (25, (1, 4), 0.55, 20, True),      'P4002': (60, (4, 10), 0.70, 50, False),
    'P5001': (100, (8, 20), 0.70, 80, False),   'P5002': (30, (2, 5), 0.50, 20, True),
    'P6001': (400, (20, 60), 0.85, 300, False), 'P6002': (100, (6, 16), 0.75, 80, False),
}
CUSTOMERS = ['鼓楼门店', '泉山门店', '云龙门店', '电商订单', '行政部领用', '矿大校区店', '企业团购', '仓间调拨']
DAYS = 13          # 过去 13 天 + 今天
SEED = 20261116    # 固定随机种子 → 每次生成的数据完全相同（日期相对于运行当天）


def plan(initial_stock, thresholds, now):
    """本地模拟，生成按时间排序的操作列表。initial_stock: data.sql 期初库存。"""
    rnd = random.Random(SEED)
    stock = dict(initial_stock)
    ops = []  # dict(day, kind, code, qty, ...)
    start_day = now.date() - dt.timedelta(days=DAYS)
    # 第 -13 天上午：新商品首批入库
    for code, (first, *_rest) in SIM.items():
        if first:
            ops.append(dict(day=-DAYS, kind='in', code=code, qty=first, remark='首批入库'))
            stock[code] = stock.get(code, 0) + first
    for d in range(-DAYS, 1):
        day_ops = []
        for code, (_f, (lo, hi), prob, restock, end_low) in SIM.items():
            th = thresholds[code]
            last_days = end_low and d >= -5
            if hi > 0 and (last_days or rnd.random() < prob):
                q = rnd.randint(lo, hi)
                if last_days:
                    # 指定“低库存”的商品：最后 6 天每天出库，使最终库存落在阈值的 30%~60%
                    target = max(1, round(th * 0.45))
                    q = max(1, -(-(stock[code] - target) // (1 - d)))
                q = min(q, stock[code] - 1) if stock[code] > 1 else 0   # 演示数据中不把库存卖到 0
                if q > 0:
                    day_ops.append(dict(day=d, kind='out', code=code, qty=q, customer=rnd.choice(CUSTOMERS)))
                    stock[code] -= q
        # 补货：每天按“库存/阈值”比值最低的 2~3 个商品下补货单（低库存商品最后 8 天不补货）
        cands = sorted((c for c, v in SIM.items() if v[3] and not (v[4] and d >= -8)),
                       key=lambda c: stock[c] / thresholds[c])
        for code in cands[:rnd.randint(2, 3)]:
            if stock[code] > thresholds[code] * 4:
                continue
            q = max(5, int(SIM[code][3] * rnd.uniform(0.5, 1.0)) // 5 * 5)
            day_ops.append(dict(day=d, kind='in', code=code, qty=q, remark='补货'))
            stock[code] += q
        rnd.shuffle(day_ops)
        ops.extend(day_ops)
    # 分配时间：每天 08:30~17:30 均匀分布；今天只用到“当前时间前 10 分钟”为止
    by_day = {}
    for o in ops:
        by_day.setdefault(o['day'], []).append(o)
    for d, lst in by_day.items():
        date = now.date() + dt.timedelta(days=d)
        t0 = dt.datetime.combine(date, dt.time(8, 30))
        t1 = dt.datetime.combine(date, dt.time(17, 30))
        if d == 0:
            t1 = min(t1, now - dt.timedelta(minutes=10))
            if t1 <= t0:  # 清晨运行：今天的单据压缩到 0 点之后
                t0 = dt.datetime.combine(date, dt.time(0, 5)); t1 = max(t0 + dt.timedelta(minutes=len(lst)), now - dt.timedelta(minutes=2))
        span = (t1 - t0).total_seconds()
        for i, o in enumerate(lst):
            o['at'] = t0 + dt.timedelta(seconds=int(span * (i + 0.5) / len(lst)) + rnd.randint(0, 50))
    ops.sort(key=lambda o: o['at'])
    return ops, stock


# ---------------------------------------------------------------- HTTP 工具
class App:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.cj = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cj))

    def get(self, path):
        return self.op.open(self.base + path, timeout=30).read().decode('utf-8')

    def csrf(self, path):
        m = re.search(r'name="_csrf" value="([^"]+)"', self.get(path))
        return m.group(1)

    def post_form(self, path, fields, csrf_from):
        fields = dict(fields, _csrf=self.csrf(csrf_from))
        req = urllib.request.Request(self.base + path, data=urllib.parse.urlencode(fields).encode('utf-8'))
        resp = self.op.open(req, timeout=30)
        return resp.geturl(), resp.read().decode('utf-8')

    def login(self, user, pw):
        url, _ = self.post_form('/login', {'username': user, 'password': pw}, '/login')
        assert '/login' not in url, '登录失败：' + user

    def api(self, path, body, user, pw):
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode('utf-8'), method='POST',
                                     headers={'Content-Type': 'application/json',
                                              'Authorization': 'Basic ' + base64.b64encode(f'{user}:{pw}'.encode()).decode()})
        try:
            r = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as e:
            raise SystemExit(f'接口失败 {path} {body}: {e.code} {e.read().decode()}')
        return json.loads(r.read().decode('utf-8'))

    def products(self):
        return json.loads(self.op.open(self.base + '/api/products', timeout=30).read().decode())['data']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--base', default='http://127.0.0.1:8080')
    ap.add_argument('--sql-out', default='/tmp/seed-demo-backfill.sql')
    a = ap.parse_args()
    now = dt.datetime.now().replace(microsecond=0)
    # data.sql 中的期初库存与阈值（与 src/main/resources/db/data.sql 保持一致）
    initial = {'P1001': 120, 'P1002': 300, 'P2001': 80, 'P2002': 8, 'P3001': 60, 'P3002': 5, 'P4001': 40, 'P9001': 50}
    th = {'P1001': 20, 'P1002': 50, 'P2001': 30, 'P2002': 15, 'P3001': 20, 'P3002': 10, 'P4001': 10, 'P9001': 10}
    th.update({p[0]: p[6] for p in NEW_PRODUCTS})
    ops, final = plan(initial, th, now)

    days = {}
    for o in ops:
        k = o['at'].strftime('%m-%d'); days.setdefault(k, [0, 0, 0, 0])
        i = 0 if o['kind'] == 'in' else 2
        days[k][i] += 1; days[k][i + 1] += o['qty']
    print('计划：%d 张单据（入库 %d / 出库 %d）' % (len(ops), sum(o['kind'] == 'in' for o in ops), sum(o['kind'] == 'out' for o in ops)))
    for k, v in days.items():
        print(f'  {k}  入库 {v[0]:2d} 单 {v[1]:5d} 件 | 出库 {v[2]:2d} 单 {v[3]:5d} 件')
    low = sorted(c for c in final if final[c] <= th[c])
    print('预计最终库存：', json.dumps(final, ensure_ascii=False))
    print('预计低库存商品：', low)
    if a.dry_run:
        return

    app = App(a.base)
    app.login('admin', 'admin123')
    # 1) 分类、供应商、商品：走表单
    for name, desc in NEW_CATEGORIES:
        url, _ = app.post_form('/categories/save', {'name': name, 'description': desc}, '/categories/new')
        assert '/categories' in url and '/new' not in url, '新增分类失败 ' + name
    cats = {m.group(2): int(m.group(1)) for m in re.finditer(r'/products\?categoryId=(\d+)">([^<]+)<', app.get('/categories'))}
    for name, cp, phone, email, addr, remark in NEW_SUPPLIERS:
        url, _ = app.post_form('/suppliers/save', {'name': name, 'contactPerson': cp, 'phone': phone, 'email': email,
                                                    'address': addr, 'remark': remark}, '/suppliers/new')
        assert '/suppliers' in url and '/new' not in url, '新增供应商失败 ' + name
    form = app.get('/products/new')
    sel = re.search(r'<select[^>]*name="supplierId".*?</select>', form, re.S).group(0)
    sups = {m.group(2).strip(): int(m.group(1)) for m in re.finditer(r'<option value="(\d+)"[^>]*>([^<]+)</option>', sel)}
    for code, name, cat, sup, unit, price, wt in NEW_PRODUCTS:
        url, _ = app.post_form('/products/save', {'code': code, 'name': name, 'categoryId': cats[cat],
                                                  'supplierId': sups[sup] if sup else '', 'unit': unit, 'price': f'{price:.2f}',
                                                  'warningThreshold': wt, 'status': 1}, '/products/new')
        assert '/products' in url and '/new' not in url, '新增商品失败 ' + code
    prods = {p['code']: p for p in app.products()}
    print(f'基础资料：分类 {len(cats)} 个，供应商 {len(sups)} 个，商品 {len(prods)} 个')

    # 2) 出入库：走 REST 接口（入库多由管理员办理，出库由操作员 / 管理员办理）
    users = [('operator', 'operator123'), ('admin', 'admin123')]
    rnd = random.Random(SEED + 1)
    backfill = []
    for i, o in enumerate(ops):
        p = prods[o['code']]
        rid = f'demo-{o["at"]:%Y%m%d}-{i:04d}'
        if o['kind'] == 'in':
            body = {'productId': p['id'], 'quantity': o['qty'], 'requestId': rid, 'remark': o['remark'],
                    'unitPrice': round(float(p['price']) * 0.62, 2)}
            sup_name = next((x[3] for x in NEW_PRODUCTS if x[0] == o['code']), None)
            if sup_name: body['supplierId'] = sups[sup_name]
            r = app.api('/api/stock/in', body, 'admin', 'admin123')
        else:
            u = users[0] if rnd.random() < 0.7 else users[1]
            r = app.api('/api/stock/out', {'productId': p['id'], 'quantity': o['qty'], 'requestId': rid,
                                           'customer': o['customer']}, *u)
        assert r['code'] == 'OK', r
        no = r['data']['orderNo']; ts = o['at'].strftime('%Y-%m-%d %H:%M:%S')
        tbl = 'stock_in_order' if o['kind'] == 'in' else 'stock_out_order'
        # 单号格式 = 前缀 + yyyyMMddHHmmssSSS + 4 位序号（OrderNoGenerator），其中时间部分随回填日期一起改写，
        # 保留原毫秒与序号保证唯一；单据表与流水表同步修改，关联关系不变
        prefix = re.match(r'[A-Z]+', no).group(0)
        new_no = prefix + o['at'].strftime('%Y%m%d%H%M%S') + no[len(prefix) + 14:]
        backfill.append(f"UPDATE {tbl} SET created_at='{ts}', order_no='{new_no}' WHERE order_no='{no}';")
        backfill.append(f"UPDATE stock_flow SET created_at='{ts}', ref_order_no='{new_no}' WHERE ref_order_no='{no}';")
    # 3) 期初数据与基础资料的时间：第 -14 天
    d0 = (now.date() - dt.timedelta(days=DAYS + 1)).strftime('%Y-%m-%d')
    backfill += [f"UPDATE stock_in_order SET created_at='{d0} 08:30:00' WHERE order_no LIKE 'IN-INIT-%';",
                 f"UPDATE stock_flow SET created_at='{d0} 08:30:00' WHERE ref_order_no LIKE 'IN-INIT-%';",
                 f"UPDATE category SET created_at='{d0} 08:00:00', updated_at='{d0} 08:00:00';",
                 f"UPDATE supplier SET created_at='{d0} 08:05:00', updated_at='{d0} 08:05:00';",
                 f"UPDATE product SET created_at='{d0} 08:10:00';",
                 f"UPDATE sys_user SET created_at='{d0} 08:00:00';"]
    open(a.sql_out, 'w', encoding='utf-8').write('\n'.join(backfill) + '\n')
    real = {c: p['stock'] for c, p in ((c, p) for c, p in {x['code']: x for x in app.products()}.items())}
    mism = {c: (final[c], real.get(c)) for c in final if real.get(c) != final[c]}
    print(f'已提交 {len(ops)} 张单据；时间回填 SQL：{a.sql_out}')
    print('模拟库存与系统实际库存比对：', '全部一致' if not mism else mism)
    if mism:
        sys.exit(2)


if __name__ == '__main__':
    main()
