package com.cumt.inventory.dto;

import java.util.List;

/**
 * 库存一致性核对报告。
 * 对每个商品校验：
 * 1) 当前库存 == Σ入库流水 − Σ出库流水；
 * 2) 流水链连续：按 id 排序，每条流水 before == 上一条 after，且 after == before ± quantity；
 * 3) 库存非负。
 */
public record ConsistencyReport(boolean allConsistent, int productCount, int inconsistentCount, List<Item> items) {

    public record Item(Long productId, String code, String name, int stock, long flowNet, long flowCount,
                       long inOrderCount, long outOrderCount, boolean chainOk, boolean consistent) {
    }
}
