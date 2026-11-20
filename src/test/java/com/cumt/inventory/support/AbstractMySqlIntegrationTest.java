package com.cumt.inventory.support;

import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.entity.Category;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.repository.*;
import com.cumt.inventory.service.StatisticsService;
import com.cumt.inventory.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集成测试基类：连接真实 MySQL 测试库 inventory_test（见 src/test/resources/application-test.yml）。
 * 每个测试用例创建自己独立的商品，互不干扰，无需清库。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractMySqlIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected ProductRepository productRepository;
    @Autowired protected CategoryRepository categoryRepository;
    @Autowired protected SupplierRepository supplierRepository;
    @Autowired protected StockInOrderRepository stockInOrderRepository;
    @Autowired protected StockOutOrderRepository stockOutOrderRepository;
    @Autowired protected StockFlowRepository stockFlowRepository;
    @Autowired protected StockService stockService;
    @Autowired protected StatisticsService statisticsService;

    /** 创建一个测试商品，并通过“期初入库”设置初始库存（保证流水与库存从一开始就一致） */
    protected Product createProduct(int initialStock) {
        Category category = categoryRepository.findById(1L).orElseThrow();
        Product p = new Product();
        p.setCode("T" + System.currentTimeMillis() % 100000000 + "_" + SEQ.incrementAndGet());
        p.setName("测试商品-" + UUID.randomUUID().toString().substring(0, 8));
        p.setCategory(category);
        p.setUnit("件");
        p.setPrice(new BigDecimal("10.00"));
        p.setWarningThreshold(5);
        p.setStatus(1);
        p = productRepository.saveAndFlush(p);
        if (initialStock > 0) {
            StockInRequest in = new StockInRequest();
            in.setProductId(p.getId());
            in.setQuantity(initialStock);
            in.setRequestId("init-" + UUID.randomUUID());
            in.setRemark("测试期初库存");
            stockService.stockIn(in, "test");
        }
        return p;
    }

    protected int stockOf(Product p) {
        return productRepository.currentStock(p.getId());
    }
}
