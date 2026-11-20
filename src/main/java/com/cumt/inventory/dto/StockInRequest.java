package com.cumt.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** 入库请求（页面表单与 REST 接口共用） */
public class StockInRequest {

    @NotNull(message = "请选择商品")
    private Long productId;

    private Long supplierId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须为正整数")
    @Max(value = 1000000, message = "单次数量不能超过 1000000")
    private Integer quantity;

    @DecimalMin(value = "0.00", message = "单价不能为负数")
    private BigDecimal unitPrice;

    /** 请求幂等号：同一值只会成功处理一次；为空时由服务端生成（即不做幂等） */
    @Size(max = 64, message = "幂等号不超过 64 个字符")
    private String requestId;

    @Size(max = 255, message = "备注不超过 255 个字符")
    private String remark;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
