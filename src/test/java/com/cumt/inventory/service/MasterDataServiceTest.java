package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.CategoryForm;
import com.cumt.inventory.dto.ProductForm;
import com.cumt.inventory.dto.SupplierForm;
import com.cumt.inventory.dto.UserForm;
import com.cumt.inventory.entity.Category;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.entity.Role;
import com.cumt.inventory.entity.Supplier;
import com.cumt.inventory.entity.User;
import com.cumt.inventory.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 集成测试：商品 / 分类 / 供应商 / 用户 的业务规则 */
class MasterDataServiceTest extends AbstractMySqlIntegrationTest {

    @Autowired private CategoryService categoryService;
    @Autowired private SupplierService supplierService;
    @Autowired private ProductService productService;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private static String uniq(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private ProductForm productForm(String code, Long categoryId) {
        ProductForm f = new ProductForm();
        f.setCode(code);
        f.setName("商品" + code);
        f.setCategoryId(categoryId);
        f.setUnit("件");
        f.setPrice(new BigDecimal("9.90"));
        f.setWarningThreshold(3);
        f.setStatus(1);
        return f;
    }

    @Test
    @DisplayName("分类：新增、修改、同名拒绝；有商品时禁止删除；无商品可删除")
    void categoryRules() {
        CategoryForm f = new CategoryForm();
        f.setName(uniq("分类"));
        Category c = categoryService.save(f);
        assertThat(c.getId()).isNotNull();

        CategoryForm dup = new CategoryForm();
        dup.setName(c.getName());
        assertThatThrownBy(() -> categoryService.save(dup)).isInstanceOf(BusinessException.class).hasMessageContaining("已存在");

        Product p = productService.save(productForm(uniq("C"), c.getId()));
        assertThatThrownBy(() -> categoryService.delete(c.getId())).isInstanceOf(BusinessException.class).hasMessageContaining("还有商品");

        productService.delete(p.getId()); // 无流水的商品可以删除
        categoryService.delete(c.getId());
        assertThat(categoryRepository.findById(c.getId())).isEmpty();
    }

    @Test
    @DisplayName("商品：编码唯一；新商品库存为 0；编辑不影响库存；有流水的商品不能删除")
    void productRules() {
        String code = uniq("P");
        Product p = productService.save(productForm(code, 1L));
        assertThat(stockOf(p)).isZero();
        assertThatThrownBy(() -> productService.save(productForm(code, 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");

        Product withStock = createProduct(15);
        ProductForm edit = productService.toForm(productService.get(withStock.getId()));
        edit.setName("改名后的商品");
        edit.setPrice(new BigDecimal("88.00"));
        productService.save(edit);
        assertThat(stockOf(withStock)).as("编辑商品资料不会修改库存").isEqualTo(15);

        assertThatThrownBy(() -> productService.delete(withStock.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("库存流水");
    }

    @Test
    @DisplayName("供应商：名称唯一；被商品引用时禁止删除")
    void supplierRules() {
        SupplierForm f = new SupplierForm();
        f.setName(uniq("供应商"));
        f.setEmail("a@b.com");
        Supplier s = supplierService.save(f);
        SupplierForm dup = new SupplierForm();
        dup.setName(s.getName());
        assertThatThrownBy(() -> supplierService.save(dup)).isInstanceOf(BusinessException.class);

        ProductForm pf = productForm(uniq("S"), 1L);
        pf.setSupplierId(s.getId());
        productService.save(pf);
        assertThatThrownBy(() -> supplierService.delete(s.getId())).isInstanceOf(BusinessException.class).hasMessageContaining("引用");
    }

    @Test
    @DisplayName("用户：密码 BCrypt 加密存储；用户名唯一；不能禁用自己")
    void userRules() {
        UserForm f = new UserForm();
        f.setUsername(uniq("u_").replace("-", ""));
        f.setRealName("测试员");
        f.setPassword("secret123");
        f.setRole(Role.OPERATOR);
        User u = userService.save(f, "admin");
        assertThat(u.getPassword()).startsWith("$2a$").isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", u.getPassword())).isTrue();

        UserForm dup = new UserForm();
        dup.setUsername(f.getUsername());
        dup.setRealName("x");
        dup.setPassword("123456");
        assertThatThrownBy(() -> userService.save(dup, "admin")).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> userService.toggleEnabled(u.getId(), u.getUsername()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能禁用");
        userService.toggleEnabled(u.getId(), "admin");
        assertThat(userService.get(u.getId()).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("新用户密码少于 6 位被拒绝")
    void shortPasswordRejected() {
        UserForm f = new UserForm();
        f.setUsername(uniq("s_").replace("-", ""));
        f.setRealName("x");
        f.setPassword("123");
        assertThatThrownBy(() -> userService.save(f, "admin")).isInstanceOf(BusinessException.class);
    }
}
