package com.cumt.inventory.dto;

import jakarta.validation.constraints.*;

/** 出库请求（页面表单与 REST 接口共用） */
public class StockOutRequest {

    @NotNull(message = "请选择商品")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须为正整数")
    @Max(value = 1000000, message = "单次数量不能超过 1000000")
    private Integer quantity;

    @Size(max = 100, message = "去向不超过 100 个字符")
    private String customer;

    /** 请求幂等号：同一值只会成功扣减一次 */
    @Size(max = 64, message = "幂等号不超过 64 个字符")
    private String requestId;

    @Size(max = 255, message = "备注不超过 255 个字符")
    private String remark;

    public StockOutRequest() {
    }

    public StockOutRequest(Long productId, Integer quantity, String requestId) {
        this.productId = productId;
        this.quantity = quantity;
        this.requestId = requestId;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
