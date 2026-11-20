#!/usr/bin/env python3
"""
JMeter 结果（CSV 格式 .jtl）分析脚本。
按采样器标签统计：样本数、吞吐量、平均/中位/P90/P95/P99/最大响应时间，
并把响应区分为：成功(200)、业务拒绝-库存不足(409)、系统错误(其它状态码或连接失败)。
用法：python3 analyze_jtl.py result.jtl [输出.json]
"""
import csv, json, sys, math
from collections import defaultdict


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0
    k = max(0, math.ceil(p / 100.0 * len(sorted_vals)) - 1)
    return sorted_vals[k]


def analyze(path):
    rows = defaultdict(list)
    with open(path, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            rows[r['label']].append(r)
    out = {}
    for label, rs in rows.items():
        el = sorted(int(r['elapsed']) for r in rs)
        start = min(int(r['timeStamp']) for r in rs)
        end = max(int(r['timeStamp']) + int(r['elapsed']) for r in rs)
        dur = max(end - start, 1) / 1000.0
        codes = defaultdict(int)
        for r in rs:
            codes[r['responseCode']] += 1
        ok = codes.get('200', 0) + (codes.get('302', 0) if label.startswith('登录') else 0)
        biz = codes.get('409', 0)
        sys_err = len(rs) - ok - biz
        out[label] = {
            'samples': len(rs),
            'duration_s': round(dur, 3),
            'throughput_per_s': round(len(rs) / dur, 2),
            'avg_ms': round(sum(el) / len(el), 2),
            'min_ms': el[0], 'p50_ms': pct(el, 50), 'p90_ms': pct(el, 90),
            'p95_ms': pct(el, 95), 'p99_ms': pct(el, 99), 'max_ms': el[-1],
            'http_200_or_302': ok,
            'business_reject_409': biz,
            'system_errors': sys_err,
            'system_error_rate_pct': round(sys_err * 100.0 / len(rs), 3),
            'business_reject_rate_pct': round(biz * 100.0 / len(rs), 3),
            'jmeter_marked_failures': sum(1 for r in rs if r['success'] != 'true'),
            'status_codes': dict(codes),
        }
    return out


if __name__ == '__main__':
    res = analyze(sys.argv[1])
    print(json.dumps(res, ensure_ascii=False, indent=2))
    if len(sys.argv) > 2:
        with open(sys.argv[2], 'w', encoding='utf-8') as f:
            json.dump(res, f, ensure_ascii=False, indent=2)
