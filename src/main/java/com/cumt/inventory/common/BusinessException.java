package com.cumt.inventory.common;

/**
 * 业务异常：可预期的业务规则违反（如名称重复、库存不足、商品停用）。
 * 运行时异常 → 在 @Transactional 方法中抛出时会触发事务回滚。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }

    public String getCode() {
        return code;
    }
}
