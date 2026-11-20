package com.cumt.inventory.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单据号生成器：前缀 + yyyyMMddHHmmssSSS（毫秒时间戳）+ 4 位循环序号，例如 OUT202611152330001230042。
 * <ul>
 *   <li>同一 JVM 内：序号由 {@link AtomicInteger} 无锁递增（CAS），同一毫秒内最多 10000 个不重复；</li>
 *   <li>多实例部署或极端情况下仍可能碰撞：数据库 order_no 列有唯一约束兜底，
 *       碰撞时事务回滚并由 StockService 换号重试，保证单据号全局唯一。</li>
 * </ul>
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private OrderNoGenerator() {
    }

    public static String next(String prefix) {
        int seq = Math.floorMod(SEQ.getAndIncrement(), 10000);
        return prefix + LocalDateTime.now().format(FMT) + String.format("%04d", seq);
    }
}
