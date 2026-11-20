package com.cumt.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 供应商表单 */
public class SupplierForm {
    private Long id;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "名称不超过 100 个字符")
    private String name;

    @Size(max = 50, message = "联系人不超过 50 个字符")
    private String contactPerson;

    @Pattern(regexp = "^$|^[0-9+\\-() ]{5,30}$", message = "电话格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100)
    private String email;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
