package com.cumt.inventory.common;

/**
 * 库存不足异常。
 * 出库时原子条件更新影响行数为 0 即抛出，所在事务整体回滚（已插入的出库单随之撤销）。
 * 接口层映射为 HTTP 409，与系统错误（5xx）明确区分，便于压测统计“业务拒绝”。
 */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String productName, int quantity) {
        super("INSUFFICIENT_STOCK", "库存不足：商品【" + productName + "】无法出库 " + quantity + " 件");
    }
}
