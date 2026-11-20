-- =====================================================================
-- 库存一致性核对 SQL（可直接在 MySQL 中执行，也被 perf/run-perf.sh 调用）
-- 用法：mysql ... -e "SET @pid = 8; SOURCE scripts/check-consistency.sql;"
--       @pid 为 NULL 时核对全部商品
-- =====================================================================

-- 1) 每个商品：当前库存 vs 流水净变动；流水条数 vs 单据数
SELECT p.id, p.code, p.stock AS current_stock,
       COALESCE(f.net, 0) AS flow_net,
       COALESCE(f.cnt, 0) AS flow_count,
       COALESCE(i.cnt, 0) AS in_orders,
       COALESCE(o.cnt, 0) AS out_orders,
       COALESCE(i.qty, 0) AS in_qty_total,
       COALESCE(o.qty, 0) AS out_qty_total,
       CASE WHEN p.stock = COALESCE(f.net, 0)
             AND COALESCE(f.cnt, 0) = COALESCE(i.cnt, 0) + COALESCE(o.cnt, 0)
             AND p.stock = COALESCE(i.qty, 0) - COALESCE(o.qty, 0)
             AND p.stock >= 0
            THEN 'OK' ELSE 'MISMATCH' END AS result
FROM product p
LEFT JOIN (SELECT product_id, SUM(CASE WHEN change_type = 'IN' THEN quantity ELSE -quantity END) net, COUNT(*) cnt
           FROM stock_flow GROUP BY product_id) f ON f.product_id = p.id
LEFT JOIN (SELECT product_id, COUNT(*) cnt, SUM(quantity) qty FROM stock_in_order GROUP BY product_id) i ON i.product_id = p.id
LEFT JOIN (SELECT product_id, COUNT(*) cnt, SUM(quantity) qty FROM stock_out_order GROUP BY product_id) o ON o.product_id = p.id
WHERE @pid IS NULL OR p.id = @pid
ORDER BY p.id;

-- 2) 流水链断裂检查：按 id 排序，本条 before_stock 必须等于上一条 after_stock；after = before ± quantity
SELECT COUNT(*) AS broken_chain_rows
FROM (SELECT product_id, change_type, quantity, before_stock, after_stock,
             LAG(after_stock) OVER (PARTITION BY product_id ORDER BY id) AS prev_after
      FROM stock_flow
      WHERE @pid IS NULL OR product_id = @pid) t
WHERE (prev_after IS NOT NULL AND prev_after <> before_stock)
   OR after_stock <> before_stock + IF(change_type = 'IN', quantity, -quantity)
   OR after_stock < 0;

-- 3) 负库存商品数（应为 0）、重复幂等号数（应为 0）
SELECT (SELECT COUNT(*) FROM product WHERE stock < 0) AS negative_stock_products,
       (SELECT COUNT(*) FROM (SELECT request_id FROM stock_out_order GROUP BY request_id HAVING COUNT(*) > 1) d) AS dup_out_request_ids,
       (SELECT COUNT(*) FROM (SELECT ref_order_no FROM stock_flow GROUP BY ref_order_no HAVING COUNT(*) > 1) d) AS dup_flow_refs;
