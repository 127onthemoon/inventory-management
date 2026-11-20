package com.cumt.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** 商品表单（不含库存：库存只能通过出入库变更） */
public class ProductForm {
    private Long id;

    @NotBlank(message = "商品编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "编码只能包含字母、数字、-、_，长度 2~50")
    private String code;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "名称不超过 100 个字符")
    private String name;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    private Long supplierId;

    @NotBlank(message = "单位不能为空")
    @Size(max = 20)
    private String unit = "件";

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Digits(integer = 10, fraction = 2, message = "单价最多两位小数")
    private BigDecimal price = BigDecimal.ZERO;

    @NotNull(message = "预警阈值不能为空")
    @Min(value = 0, message = "预警阈值不能为负数")
    @Max(value = 1000000, message = "预警阈值过大")
    private Integer warningThreshold = 10;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
