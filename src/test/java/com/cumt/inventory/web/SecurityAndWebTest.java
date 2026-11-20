package com.cumt.inventory.web;

import com.cumt.inventory.entity.Product;
import com.cumt.inventory.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web 层集成测试（MockMvc + 真实 MySQL）：登录、角色权限、CSRF、表单校验、REST 接口状态码。
 */
@AutoConfigureMockMvc
class SecurityAndWebTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    // ------------------------------------------------------------ 登录

    @Test
    @DisplayName("默认管理员 admin/admin123 登录成功，跳转首页")
    void adminLoginSuccess() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("admin123"))
                .andExpect(authenticated().withRoles("ADMIN"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("默认操作员 operator/operator123 登录成功，角色为 OPERATOR")
    void operatorLoginSuccess() throws Exception {
        mvc.perform(formLogin("/login").user("operator").password("operator123"))
                .andExpect(authenticated().withRoles("OPERATOR"));
    }

    @Test
    @DisplayName("密码错误登录失败，跳转 /login?error")
    void loginFailure() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("未登录访问页面 → 302 跳转登录页；访问接口 → 401 JSON")
    void anonymousAccess() throws Exception {
        mvc.perform(get("/products")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ------------------------------------------------------------ 角色权限

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("操作员：可访问业务页面，不能访问用户管理和基础资料维护（403）")
    void operatorPermissions() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/products")).andExpect(status().isOk());
        mvc.perform(get("/stock/out/new")).andExpect(status().isOk());
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(get("/products/new")).andExpect(status().isForbidden());
        mvc.perform(post("/categories/save").with(csrf()).param("name", "越权分类")).andExpect(status().isForbidden());
        mvc.perform(post("/products/1/delete").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("管理员：可访问全部页面")
    void adminPermissions() throws Exception {
        for (String url : new String[]{"/", "/products", "/products/new", "/categories", "/suppliers",
                "/stock/in", "/stock/out", "/flows", "/consistency", "/users", "/users/new"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("CSRF：页面表单 POST 不带 token 被拒绝（403）")
    void csrfRequired() throws Exception {
        mvc.perform(post("/categories/save").param("name", "无token分类")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ 表单校验

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("商品表单校验：编码为空、单价为负 → 返回表单页并提示字段错误")
    void productFormValidation() throws Exception {
        mvc.perform(post("/products/save").with(csrf())
                        .param("code", "").param("name", "x").param("categoryId", "1")
                        .param("unit", "件").param("price", "-1").param("warningThreshold", "1").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("form", "code", "price"));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("出库表单：库存不足时留在表单页并提示错误，库存不变")
    void stockOutFormInsufficient() throws Exception {
        Product p = createProduct(2);
        mvc.perform(post("/stock/out").with(csrf())
                        .param("productId", p.getId().toString()).param("quantity", "5")
                        .param("requestId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("stock/out-form"))
                .andExpect(model().attribute("error", org.hamcrest.Matchers.containsString("库存不足")));
        assertThat(stockOf(p)).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("出库表单：同一表单提交两次，只扣减一次，第二次提示重复提交")
    void stockOutFormDoubleSubmit() throws Exception {
        Product p = createProduct(10);
        String rid = UUID.randomUUID().toString();
        mvc.perform(post("/stock/out").with(csrf()).param("productId", p.getId().toString())
                        .param("quantity", "3").param("requestId", rid))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        mvc.perform(post("/stock/out").with(csrf()).param("productId", p.getId().toString())
                        .param("quantity", "3").param("requestId", rid))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("warning"));
        assertThat(stockOf(p)).isEqualTo(7);
    }

    // ------------------------------------------------------------ REST 接口

    @Test
    @DisplayName("REST：出库成功 200/OK；重复 200/DUPLICATE；库存不足 409；参数错误 400；商品不存在 404")
    void restStatusCodes() throws Exception {
        Product p = createProduct(3);
        String body = "{\"productId\":" + p.getId() + ",\"quantity\":2,\"requestId\":\"rest-" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.afterStock").value(1));
        mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("DUPLICATE"));
        mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p.getId() + ",\"quantity\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + p.getId() + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":99999999,\"quantity\":1}"))
                .andExpect(status().isNotFound());
        assertThat(stockOf(p)).isEqualTo(1);
    }

    @Test
    @DisplayName("REST：Idempotency-Key 请求头同样具备幂等效果")
    void restIdempotencyHeader() throws Exception {
        Product p = createProduct(5);
        String key = "hdr-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/stock/out").with(httpBasic("operator", "operator123"))
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":" + p.getId() + ",\"quantity\":1}"))
                    .andExpect(status().isOk());
        }
        assertThat(stockOf(p)).isEqualTo(4);
    }
}
