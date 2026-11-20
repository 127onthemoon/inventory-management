package com.cumt.inventory.common;

/**
 * REST 接口统一返回结构。
 *
 * @param success 是否成功
 * @param code    结果码：OK / DUPLICATE / INSUFFICIENT_STOCK / VALIDATION_ERROR / ...
 * @param message 提示信息
 * @param data    业务数据
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "操作成功", data);
    }

    public static <T> ApiResponse<T> ok(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
