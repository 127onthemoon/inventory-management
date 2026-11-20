package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.InsufficientStockException;
import com.cumt.inventory.common.OrderNoGenerator;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.entity.*;
import com.cumt.inventory.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存变更的【事务核心】。
 *
 * <h2>并发控制设计（本项目最核心的代码）</h2>
 * 一次出库在同一个数据库事务中依次执行：
 * <ol>
 *   <li><b>原子条件扣减</b>：{@code UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?}。
 *       影响行数为 0 说明库存不足，立即抛出 {@link InsufficientStockException}，事务回滚。
 *       执行成功后，本事务持有该商品行的 InnoDB 排他锁，直到提交。</li>
 *   <li><b>当前读</b>：{@code SELECT stock ... FOR UPDATE} 读出变动后库存（本事务已持锁，不会等待），
 *       变动前库存 = 变动后 + 数量。由于其它事务此时无法修改该行，这两个值一定准确。</li>
 *   <li><b>写单据</b>：出库单的 {@code request_id}、{@code order_no} 列上有唯一约束。
 *       若同一幂等号已被处理过，插入会触发唯一键冲突 → 整个事务回滚 → 第 1 步的扣减被撤销，
 *       因此“重复提交”不可能造成重复扣减。</li>
 *   <li><b>写流水</b>：记录类型、数量、变动前/后库存、关联单据号（ref_order_no 也唯一）。</li>
 * </ol>
 * 以上四步要么全部成功提交，要么全部回滚，保证“库存 + 单据 + 流水”三者强一致。
 *
 * <h2>为什么不用 synchronized / 先查后改</h2>
 * <ul>
 *   <li>synchronized 只在单个 JVM 内有效，多实例部署立即失效；并且锁的范围与事务边界不一致
 *       （方法返回时锁已释放，但事务可能尚未提交），仍会出现超卖。</li>
 *   <li>“SELECT 读库存 → Java 计算 stock-1 → UPDATE 写回”存在检查与写入之间的时间窗口，
 *       两个事务读到同一个旧值会产生“丢失更新”（超卖）。</li>
 * </ul>
 * 本方案把“检查 + 扣减”交给数据库在一条语句内完成，由 InnoDB 行锁天然串行化，简单且可靠。
 *
 * <p>本类只负责“单次尝试”的事务；幂等快速返回、死锁重试等逻辑在外层 {@link StockService}。
 * 之所以拆成两个 Bean，是因为 Spring 的 @Transactional 基于代理，必须从外部调用才能生效，
 * 且重试必须在“事务之外”进行（每次重试开启一个全新的事务）。
 */
@Service
public class StockTxService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockInOrderRepository stockInOrderRepository;
    private final StockOutOrderRepository stockOutOrderRepository;
    private final StockFlowRepository stockFlowRepository;

    public StockTxService(ProductRepository productRepository,
                          SupplierRepository supplierRepository,
                          StockInOrderRepository stockInOrderRepository,
                          StockOutOrderRepository stockOutOrderRepository,
                          StockFlowRepository stockFlowRepository) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.stockInOrderRepository = stockInOrderRepository;
        this.stockOutOrderRepository = stockOutOrderRepository;
        this.stockFlowRepository = stockFlowRepository;
    }

    /**
     * 出库（单次事务尝试）。
     * 隔离级别使用 MySQL 默认的 REPEATABLE READ；正确性不依赖隔离级别，
     * 而依赖 UPDATE 的“当前读 + 行锁”语义。任何运行时异常都会使整个事务回滚。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public StockOperationResult doStockOut(StockOutRequest req, String requestId, String operator) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在：id=" + req.getProductId()));
        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_DISABLED", "商品【" + product.getName() + "】已停用，不能出库");
        }
        int qty = req.getQuantity();

        // ① 原子条件扣减：检查与扣减在一条 SQL 中完成，影响行数 0 = 库存不足
        int updated = productRepository.decreaseStock(product.getId(), qty);
        if (updated == 0) {
            // 抛出运行时异常 → 事务回滚（此时尚未写任何单据/流水，回滚后无任何痕迹）
            throw new InsufficientStockException(product.getName(), qty);
        }

        // ② 当前读：本事务已持有行锁，读到的就是本次扣减后的库存
        int afterStock = productRepository.currentStockForUpdate(product.getId());
        int beforeStock = afterStock + qty;

        // ③ 写出库单：request_id 唯一约束兜底防重复扣减；saveAndFlush 让冲突立即在此处暴露
        StockOutOrder order = new StockOutOrder();
        order.setOrderNo(OrderNoGenerator.next("OUT"));
        order.setRequestId(requestId);
        order.setProduct(productRepository.getReferenceById(product.getId()));
        order.setQuantity(qty);
        order.setCustomer(req.getCustomer());
        order.setBeforeStock(beforeStock);
        order.setAfterStock(afterStock);
        order.setOperator(operator);
        order.setRemark(req.getRemark());
        stockOutOrderRepository.saveAndFlush(order);

        // ④ 写库存流水
        saveFlow(product.getId(), ChangeType.OUT, qty, beforeStock, afterStock, order.getOrderNo(), operator);

        return new StockOperationResult(ChangeType.OUT, order.getOrderNo(), requestId, product.getId(),
                qty, beforeStock, afterStock, false);
    }

    /**
     * 入库（单次事务尝试）。与出库对称：原子 stock = stock + ? → 当前读 → 写入库单 → 写流水。
     * 入库与出库对同一商品行使用同一把行锁，二者互斥执行，不会互相覆盖（无丢失更新）。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public StockOperationResult doStockIn(StockInRequest req, String requestId, String operator) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("商品不存在：id=" + req.getProductId()));
        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_DISABLED", "商品【" + product.getName() + "】已停用，不能入库");
        }
        Supplier supplier = null;
        if (req.getSupplierId() != null) {
            supplier = supplierRepository.findById(req.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("供应商不存在：id=" + req.getSupplierId()));
        }
        int qty = req.getQuantity();

        // ① 原子增加库存
        int updated = productRepository.increaseStock(product.getId(), qty);
        if (updated == 0) {
            throw new BusinessException("PRODUCT_DISABLED", "商品【" + product.getName() + "】不可入库");
        }

        // ② 当前读得到变动后库存
        int afterStock = productRepository.currentStockForUpdate(product.getId());
        int beforeStock = afterStock - qty;

        // ③ 写入库单（request_id 唯一）
        StockInOrder order = new StockInOrder();
        order.setOrderNo(OrderNoGenerator.next("IN"));
        order.setRequestId(requestId);
        order.setProduct(productRepository.getReferenceById(product.getId()));
        order.setSupplier(supplier);
        order.setQuantity(qty);
        order.setUnitPrice(req.getUnitPrice());
        order.setBeforeStock(beforeStock);
        order.setAfterStock(afterStock);
        order.setOperator(operator);
        order.setRemark(req.getRemark());
        stockInOrderRepository.saveAndFlush(order);

        // ④ 写库存流水
        saveFlow(product.getId(), ChangeType.IN, qty, beforeStock, afterStock, order.getOrderNo(), operator);

        return new StockOperationResult(ChangeType.IN, order.getOrderNo(), requestId, product.getId(),
                qty, beforeStock, afterStock, false);
    }

    private void saveFlow(Long productId, ChangeType type, int qty, int before, int after,
                          String orderNo, String operator) {
        StockFlow flow = new StockFlow();
        flow.setProduct(productRepository.getReferenceById(productId));
        flow.setChangeType(type);
        flow.setQuantity(qty);
        flow.setBeforeStock(before);
        flow.setAfterStock(after);
        flow.setRefOrderNo(orderNo);
        flow.setOperator(operator);
        // 立即 flush：任何约束错误都在事务内抛出并触发整体回滚，而不是拖到提交阶段
        stockFlowRepository.saveAndFlush(flow);
    }
}
