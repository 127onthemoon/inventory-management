package com.cumt.inventory.dto;

import com.cumt.inventory.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户表单。新增时密码必填；编辑时密码留空表示不修改。 */
public class UserForm {
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$", message = "用户名为 3~50 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50)
    private String realName;

    @Size(max = 64, message = "密码不超过 64 位")
    private String password;

    @NotNull(message = "请选择角色")
    private Role role = Role.OPERATOR;

    private boolean enabled = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
