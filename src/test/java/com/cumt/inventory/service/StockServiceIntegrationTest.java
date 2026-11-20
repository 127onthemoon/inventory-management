package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.InsufficientStockException;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.ConsistencyReport;
import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.entity.StockFlow;
import com.cumt.inventory.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集成测试（真实 MySQL）：入库、出库、库存不足拒绝、重复提交、异常回滚、数据库约束兜底。
 */
class StockServiceIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private StockOutRequest out(Product p, int qty) {
        return new StockOutRequest(p.getId(), qty, UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("入库：库存增加，生成入库单与 IN 流水，变动前后库存正确")
    void stockInIncreasesStock() {
        Product p = createProduct(10);
        StockInRequest in = new StockInRequest();
        in.setProductId(p.getId());
        in.setQuantity(25);
        in.setRequestId(UUID.randomUUID().toString());
        StockOperationResult r = stockService.stockIn(in, "tester");

        assertThat(r.beforeStock()).isEqualTo(10);
        assertThat(r.afterStock()).isEqualTo(35);
        assertThat(stockOf(p)).isEqualTo(35);
        List<StockFlow> flows = stockFlowRepository.findByProductIdOrderByIdAsc(p.getId());
        assertThat(flows).hasSize(2);
        StockFlow last = flows.get(1);
        assertThat(last.getChangeType()).isEqualTo(ChangeType.IN);
        assertThat(last.getQuantity()).isEqualTo(25);
        assertThat(last.getBeforeStock()).isEqualTo(10);
        assertThat(last.getAfterStock()).isEqualTo(35);
        assertThat(last.getRefOrderNo()).isEqualTo(r.orderNo());
        assertThat(last.getOperator()).isEqualTo("tester");
    }

    @Test
    @DisplayName("出库：库存扣减，生成出库单与 OUT 流水")
    void stockOutDecreasesStock() {
        Product p = createProduct(20);
        StockOperationResult r = stockService.stockOut(out(p, 7), "tester");
        assertThat(r.beforeStock()).isEqualTo(20);
        assertThat(r.afterStock()).isEqualTo(13);
        assertThat(stockOf(p)).isEqualTo(13);
        assertThat(stockOutOrderRepository.countByProductId(p.getId())).isEqualTo(1);
        assertThat(stockFlowRepository.countByProductId(p.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("出库数量恰好等于库存：允许，库存变为 0")
    void stockOutExactlyAll() {
        Product p = createProduct(5);
        stockService.stockOut(out(p, 5), "tester");
        assertThat(stockOf(p)).isZero();
    }

    @Test
    @DisplayName("库存不足：拒绝出库，库存不变，不产生单据与流水")
    void insufficientStockRejected() {
        Product p = createProduct(3);
        assertThatThrownBy(() -> stockService.stockOut(out(p, 4), "tester"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("库存不足");
        assertThat(stockOf(p)).isEqualTo(3);
        assertThat(stockOutOrderRepository.countByProductId(p.getId())).isZero();
        assertThat(stockFlowRepository.countByProductId(p.getId())).isEqualTo(1); // 只有期初入库流水
    }

    @Test
    @DisplayName("库存为 0 时出库 1 件被拒绝")
    void zeroStockRejected() {
        Product p = createProduct(0);
        assertThatThrownBy(() -> stockService.stockOut(out(p, 1), "tester"))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stockOf(p)).isZero();
    }

    @Test
    @DisplayName("重复提交同一 requestId（串行）：只扣减一次，第二次返回 duplicate=true 且单据号相同")
    void duplicateSubmitDeductOnce() {
        Product p = createProduct(10);
        String rid = UUID.randomUUID().toString();
        StockOperationResult first = stockService.stockOut(new StockOutRequest(p.getId(), 2, rid), "tester");
        StockOperationResult second = stockService.stockOut(new StockOutRequest(p.getId(), 2, rid), "tester");
        StockOperationResult third = stockService.stockOut(new StockOutRequest(p.getId(), 2, rid), "tester");

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(third.duplicate()).isTrue();
        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(stockOf(p)).isEqualTo(8);
        assertThat(stockOutOrderRepository.countByProductId(p.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("异常回滚：扣减成功后写单据失败（备注超长触发数据库错误），库存扣减被整体回滚")
    void rollbackWhenOrderInsertFails() {
        Product p = createProduct(10);
        StockOutRequest req = out(p, 3);
        req.setRemark("超长备注".repeat(100)); // 400 字符 > VARCHAR(255)，绕过控制器校验，直接在数据库层失败

        assertThatThrownBy(() -> stockService.stockOut(req, "tester"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("DATA_INTEGRITY");

        assertThat(stockOf(p)).as("UPDATE 已执行但事务回滚，库存应恢复").isEqualTo(10);
        assertThat(stockOutOrderRepository.countByProductId(p.getId())).isZero();
        assertThat(stockFlowRepository.countByProductId(p.getId())).isEqualTo(1);
        // 回滚后同一 requestId 可以重新提交（幂等号没有被“占用”）
        req.setRemark("正常备注");
        StockOperationResult retry = stockService.stockOut(req, "tester");
        assertThat(retry.duplicate()).isFalse();
        assertThat(stockOf(p)).isEqualTo(7);
    }

    @Test
    @DisplayName("停用商品不能出入库")
    void disabledProductRejected() {
        Product p = createProduct(10);
        jdbc.update("UPDATE product SET status = 0 WHERE id = ?", p.getId());
        assertThatThrownBy(() -> stockService.stockOut(out(p, 1), "tester"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("停用");
        StockInRequest in = new StockInRequest();
        in.setProductId(p.getId());
        in.setQuantity(1);
        assertThatThrownBy(() -> stockService.stockIn(in, "tester"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("停用");
        assertThat(stockOf(p)).isEqualTo(10);
    }

    @Test
    @DisplayName("商品不存在：抛出 ResourceNotFoundException")
    void productNotFound() {
        assertThatThrownBy(() -> stockService.stockOut(new StockOutRequest(99999999L, 1, "x-" + UUID.randomUUID()), "t"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("数据库 CHECK 约束兜底：即使绕过应用直接写 SQL，库存也不能为负")
    void databaseCheckConstraint() {
        Product p = createProduct(2);
        // MySQL 违反 CHECK 约束的错误码为 3819，Spring 未将其归类，因而抛出 UncategorizedSQLException（DataAccessException 子类）
        assertThatThrownBy(() -> jdbc.update("UPDATE product SET stock = -1 WHERE id = ?", p.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_product_stock_non_negative");
        assertThat(stockOf(p)).isEqualTo(2);
    }

    @Test
    @DisplayName("数据库唯一约束兜底：直接插入重复 request_id 被拒绝")
    void databaseUniqueRequestId() {
        Product p = createProduct(5);
        StockOperationResult r = stockService.stockOut(out(p, 1), "tester");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_out_order(order_no, request_id, product_id, quantity, before_stock, after_stock, operator) " +
                        "VALUES ('OUT-MANUAL-1', ?, ?, 1, 4, 3, 'x')", r.requestId(), p.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("一致性核对：多次出入库后 库存 = Σ入库 − Σ出库，流水链连续")
    void consistencyAfterOperations() {
        Product p = createProduct(30);
        stockService.stockOut(out(p, 5), "t");
        StockInRequest in = new StockInRequest();
        in.setProductId(p.getId());
        in.setQuantity(12);
        stockService.stockIn(in, "t");
        stockService.stockOut(out(p, 8), "t");
        assertThatThrownBy(() -> stockService.stockOut(out(p, 1000), "t")).isInstanceOf(InsufficientStockException.class);

        ConsistencyReport.Item item = statisticsService.check(productRepository.findById(p.getId()).orElseThrow());
        assertThat(item.stock()).isEqualTo(30 - 5 + 12 - 8);
        assertThat(item.flowNet()).isEqualTo(item.stock());
        assertThat(item.flowCount()).isEqualTo(4);
        assertThat(item.chainOk()).isTrue();
        assertThat(item.consistent()).isTrue();
    }
}
