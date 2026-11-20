package com.cumt.inventory.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品。
 * <p>
 * 注意：stock 字段标记为 insertable/updatable = false ——
 * Hibernate 永远不会通过“实体脏检查”把内存中的库存写回数据库，
 * 库存只能经由 {@code ProductRepository} 中的原子条件更新语句变化。
 * 这从框架层面杜绝了“读库存 → Java 内存计算 → 写回”的并发错误写法。
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false, length = 20)
    private String unit = "件";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** 当前库存：只读映射（新建商品时由数据库默认值 0 初始化） */
    @Column(nullable = false, insertable = false, updatable = false)
    private Integer stock = 0;

    @Column(name = "warning_threshold", nullable = false)
    private Integer warningThreshold = 10;

    /** 1 在售，0 停用 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 是否低库存（库存 ≤ 预警阈值） */
    public boolean isLowStock() {
        return stock != null && warningThreshold != null && stock <= warningThreshold;
    }

    public boolean isActive() {
        return status != null && status == 1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public Integer getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
