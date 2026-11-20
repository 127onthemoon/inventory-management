package com.cumt.inventory.entity;

/** 用户角色。 */
public enum Role {
    /** 管理员：全部权限 */
    ADMIN("管理员"),
    /** 操作员：日常出入库作业 */
    OPERATOR("操作员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
