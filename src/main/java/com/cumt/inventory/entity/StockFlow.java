package com.cumt.inventory.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 库存流水：每一次库存变动对应一条记录。
 * 不变式：afterStock = beforeStock ± quantity；同一商品按 id 排序时，
 * 每条流水的 beforeStock 等于上一条流水的 afterStock（流水链连续）。
 */
@Entity
@Table(name = "stock_flow")
public class StockFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 10)
    private ChangeType changeType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "before_stock", nullable = false)
    private Integer beforeStock;

    @Column(name = "after_stock", nullable = false)
    private Integer afterStock;

    /** 关联单据号（唯一：一张单据只对应一条流水） */
    @Column(name = "ref_order_no", nullable = false, unique = true, length = 40)
    private String refOrderNo;

    @Column(nullable = false, length = 50)
    private String operator;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getBeforeStock() { return beforeStock; }
    public void setBeforeStock(Integer beforeStock) { this.beforeStock = beforeStock; }
    public Integer getAfterStock() { return afterStock; }
    public void setAfterStock(Integer afterStock) { this.afterStock = afterStock; }
    public String getRefOrderNo() { return refOrderNo; }
    public void setRefOrderNo(String refOrderNo) { this.refOrderNo = refOrderNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
