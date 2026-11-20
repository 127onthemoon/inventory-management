package com.cumt.inventory.web;

import com.cumt.inventory.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 前端重构回归测试（MockMvc + 真实 MySQL）：
 * 1) 本地字体 / 图标等静态资源无需登录即可访问（登录页也要用），且不依赖任何外网 CDN；
 * 2) 新版布局的关键结构（侧栏导航、确认弹窗、图表数据表、库存水位条）在页面中正确渲染；
 * 3) 按角色渲染：操作员看不到“用户管理”菜单和“新增商品”按钮。
 */
@AutoConfigureMockMvc
class FrontendPageTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("本地字体与 SVG 图标无需登录即可访问；登录页不引用外网资源")
    void localAssetsArePublicAndNoCdn() throws Exception {
        mvc.perform(get("/css/icons.svg")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"i-warehouse\"")));
        mvc.perform(get("/css/fonts/ibm-plex-sans-latin-400-normal.woff2")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("stock &gt;= :qty")))
                .andExpect(content().string(not(containsString("https://"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("管理员首页：侧栏、指标条、趋势图数据表、确认弹窗齐全")
    void dashboardLayoutForAdmin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"sidebar\"")))
                .andExpect(content().string(containsString("class=\"kpis\"")))
                .andExpect(content().string(containsString("data-chart-source")))
                .andExpect(content().string(containsString("id=\"confirm-dialog\"")))
                .andExpect(content().string(containsString("用户管理")));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("操作员：商品列表有库存水位条，但没有“新增商品”按钮和“用户管理”菜单")
    void productListForOperator() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"gauge")))
                .andExpect(content().string(not(containsString("新增商品"))))
                .andExpect(content().string(not(containsString("用户管理"))));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("出库表单：保留 requestId 幂等号隐藏域，并渲染库存预览面板")
    void stockOutFormKeepsIdempotencyKey() throws Exception {
        mvc.perform(get("/stock/out/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"requestId\"")))
                .andExpect(content().string(containsString("data-preview=\"out\"")));
    }
}
