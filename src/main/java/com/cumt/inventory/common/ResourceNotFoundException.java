package com.cumt.inventory.common;

/** 资源不存在（映射为 HTTP 404）。 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
