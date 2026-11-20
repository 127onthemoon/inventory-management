-- =====================================================================
-- 初始化数据（INSERT IGNORE：主键/唯一键已存在则跳过，可重复执行）
-- 默认账号：admin / admin123（管理员），operator / operator123（操作员）
-- 注意：商品初始库存通过“期初入库单 + 入库流水”写入，保证
--       product.stock = Σ入库流水 − Σ出库流水 从第一天起就成立。
-- =====================================================================
INSERT IGNORE INTO sys_user (id, username, password, real_name, role, enabled) VALUES
 (1, 'admin',    '$2a$10$//pSy7qvntucYiUK20MO0efQpy8afbX/2a7Ek/dSUALvN048075YS', '系统管理员', 'ADMIN',    1),
 (2, 'operator', '$2a$10$1ipPtxuFp/oCeYzBYG9mIuqT91mqfYMIdAi2ySY5vlN1uU2e3ERxy', '仓库操作员', 'OPERATOR', 1);

INSERT IGNORE INTO category (id, name, description) VALUES
 (1, '电子产品', '手机、电脑及配件'),
 (2, '办公用品', '纸张、文具、打印耗材'),
 (3, '食品饮料', '零食、饮料、日用食品'),
 (4, '日用百货', '清洁、收纳、生活用品');

INSERT IGNORE INTO supplier (id, name, contact_person, phone, email, address, remark) VALUES
 (1, '徐州华辰电子有限公司', '张经理', '0516-83880001', 'sales@hc-elec.example.com', '江苏省徐州市泉山区', '电子产品主供应商'),
 (2, '南京文汇办公用品公司', '李主管', '025-86660002',  'office@wh.example.com',      '江苏省南京市鼓楼区', NULL),
 (3, '苏州优鲜食品有限公司', '王女士', '0512-65550003', 'fresh@yx.example.com',       '江苏省苏州市工业园区', '冷链配送');

INSERT IGNORE INTO product (id, code, name, category_id, supplier_id, unit, price, stock, warning_threshold, status) VALUES
 (1, 'P1001', '无线蓝牙耳机',    1, 1, '副', 199.00, 120, 20, 1),
 (2, 'P1002', 'USB-C 快充数据线', 1, 1, '根',  29.90, 300, 50, 1),
 (3, 'P2001', 'A4 复印纸(500张)', 2, 2, '包',  25.50,  80, 30, 1),
 (4, 'P2002', '黑色中性笔(12支)', 2, 2, '盒',  18.00,   8, 15, 1),
 (5, 'P3001', '矿泉水 550ml',     3, 3, '箱',  24.00,  60, 20, 1),
 (6, 'P3002', '全麦苏打饼干',     3, 3, '袋',  12.80,   5, 10, 1),
 (7, 'P4001', '抽纸(3层*100抽)',  4, NULL, '提', 15.90, 40, 10, 1),
 (8, 'P9001', '秒杀演示商品',     1, 1, '件',  99.00,  50, 10, 1);

-- 期初入库单与对应流水（before=0, after=期初库存）
INSERT IGNORE INTO stock_in_order (id, order_no, request_id, product_id, supplier_id, quantity, unit_price, before_stock, after_stock, operator, remark) VALUES
 (1, 'IN-INIT-0001', 'init-1', 1, 1,    120, 150.00, 0, 120, 'admin', '期初库存'),
 (2, 'IN-INIT-0002', 'init-2', 2, 1,    300,  15.00, 0, 300, 'admin', '期初库存'),
 (3, 'IN-INIT-0003', 'init-3', 3, 2,     80,  20.00, 0,  80, 'admin', '期初库存'),
 (4, 'IN-INIT-0004', 'init-4', 4, 2,      8,  12.00, 0,   8, 'admin', '期初库存'),
 (5, 'IN-INIT-0005', 'init-5', 5, 3,     60,  18.00, 0,  60, 'admin', '期初库存'),
 (6, 'IN-INIT-0006', 'init-6', 6, 3,      5,   8.00, 0,   5, 'admin', '期初库存'),
 (7, 'IN-INIT-0007', 'init-7', 7, NULL,  40,  10.00, 0,  40, 'admin', '期初库存'),
 (8, 'IN-INIT-0008', 'init-8', 8, 1,     50,  60.00, 0,  50, 'admin', '期初库存');

INSERT IGNORE INTO stock_flow (id, product_id, change_type, quantity, before_stock, after_stock, ref_order_no, operator) VALUES
 (1, 1, 'IN', 120, 0, 120, 'IN-INIT-0001', 'admin'),
 (2, 2, 'IN', 300, 0, 300, 'IN-INIT-0002', 'admin'),
 (3, 3, 'IN',  80, 0,  80, 'IN-INIT-0003', 'admin'),
 (4, 4, 'IN',   8, 0,   8, 'IN-INIT-0004', 'admin'),
 (5, 5, 'IN',  60, 0,  60, 'IN-INIT-0005', 'admin'),
 (6, 6, 'IN',   5, 0,   5, 'IN-INIT-0006', 'admin'),
 (7, 7, 'IN',  40, 0,  40, 'IN-INIT-0007', 'admin'),
 (8, 8, 'IN',  50, 0,  50, 'IN-INIT-0008', 'admin');
