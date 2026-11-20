package com.cumt.inventory.controller.api;

import com.cumt.inventory.entity.Product;

import java.math.BigDecimal;

/** 商品 JSON 视图（避免直接序列化 JPA 实体） */
public record ProductView(Long id, String code, String name, String category, String supplier, String unit,
                          BigDecimal price, int stock, int warningThreshold, boolean lowStock, int status) {

    public static ProductView of(Product p, int stock) {
        return new ProductView(p.getId(), p.getCode(), p.getName(), p.getCategory().getName(),
                p.getSupplier() == null ? null : p.getSupplier().getName(), p.getUnit(), p.getPrice(),
                stock, p.getWarningThreshold(), stock <= p.getWarningThreshold(), p.getStatus());
    }
}
