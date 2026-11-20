package com.cumt.inventory.dto;

import com.cumt.inventory.entity.ChangeType;

/**
 * 出入库操作结果。
 *
 * @param duplicate true 表示这是一次重复提交：没有再次变更库存，返回的是第一次处理的结果
 */
public record StockOperationResult(
        ChangeType type,
        String orderNo,
        String requestId,
        Long productId,
        int quantity,
        int beforeStock,
        int afterStock,
        boolean duplicate) {
}
