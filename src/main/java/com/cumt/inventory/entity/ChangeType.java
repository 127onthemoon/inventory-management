package com.cumt.inventory.entity;

/** 库存变动类型。 */
public enum ChangeType {
    IN("入库"),
    OUT("出库");

    private final String label;

    ChangeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
