package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.InsufficientStockException;
import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.entity.StockInOrder;
import com.cumt.inventory.entity.StockOutOrder;
import com.cumt.inventory.repository.StockInOrderRepository;
import com.cumt.inventory.repository.StockOutOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * 出入库【门面服务】（不开启事务）：负责幂等与重试，真正的事务逻辑在 {@link StockTxService}。
 *
 * <h2>防重复提交（幂等）的三层保护</h2>
 * <ol>
 *   <li><b>前端</b>：表单打开时生成 UUID 作为 requestId 隐藏域；提交按钮点击后立即禁用。</li>
 *   <li><b>快速路径</b>：事务开始前先按 requestId 查询，已处理过则直接返回第一次的结果（duplicate=true）。</li>
 *   <li><b>数据库唯一约束（最终保证）</b>：两个相同 requestId 的请求同时通过了快速路径检查时，
 *       它们会在商品行锁上排队；后一个事务插入单据时触发 uk_out_request_id 唯一键冲突 → 整体回滚
 *       （扣减被撤销）→ 本类捕获异常后查询已存在的单据并返回 duplicate=true。</li>
 * </ol>
 *
 * <h2>并发冲突重试</h2>
 * InnoDB 在高并发下可能检测到死锁（错误码 1213）或锁等待超时（1205），Spring 将其翻译为
 * {@link PessimisticLockingFailureException}。被选为牺牲者的事务已被数据库完整回滚，
 * 因此可以安全地开启新事务重试（最多 {@value #MAX_ATTEMPTS} 次）。
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    static final int MAX_ATTEMPTS = 3;

    private final StockTxService txService;
    private final StockInOrderRepository stockInOrderRepository;
    private final StockOutOrderRepository stockOutOrderRepository;

    public StockService(StockTxService txService,
                        StockInOrderRepository stockInOrderRepository,
                        StockOutOrderRepository stockOutOrderRepository) {
        this.txService = txService;
        this.stockInOrderRepository = stockInOrderRepository;
        this.stockOutOrderRepository = stockOutOrderRepository;
    }

    /** 出库：成功返回结果；库存不足抛 {@link InsufficientStockException}；重复提交返回 duplicate=true。 */
    public StockOperationResult stockOut(StockOutRequest req, String operator) {
        validateQuantity(req.getQuantity());
        String requestId = normalizeRequestId(req.getRequestId());

        // 快速路径：已处理过的幂等号直接返回第一次的结果
        Optional<StockOutOrder> existing = stockOutOrderRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return duplicateOf(existing.get());
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return txService.doStockOut(req, requestId, operator);
            } catch (InsufficientStockException e) {
                // 可能是“同一幂等号的并发请求”抢先成功把库存用完了：此时应视为重复提交而非库存不足
                Optional<StockOutOrder> done = stockOutOrderRepository.findByRequestId(requestId);
                if (done.isPresent()) {
                    return duplicateOf(done.get());
                }
                throw e;
            } catch (DataIntegrityViolationException e) {
                // 唯一约束冲突：先判断是不是重复提交
                Optional<StockOutOrder> done = stockOutOrderRepository.findByRequestId(requestId);
                if (done.isPresent()) {
                    return duplicateOf(done.get());
                }
                if (isOrderNoConflict(e) && attempt < MAX_ATTEMPTS) {
                    continue; // 单据号碰撞（概率极低），换一个单据号重试
                }
                log.warn("出库数据完整性错误，事务已回滚：{}", rootMessage(e));
                throw new BusinessException("DATA_INTEGRITY", "数据校验失败，操作已整体回滚");
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("出库并发冲突重试 {} 次仍失败：{}", attempt, rootMessage(e));
                    throw new BusinessException("CONCURRENT_CONFLICT", "系统繁忙（并发冲突），请稍后重试");
                }
                backoff(attempt);
            }
        }
    }

    /** 入库：逻辑与出库对称（入库没有“库存不足”）。 */
    public StockOperationResult stockIn(StockInRequest req, String operator) {
        validateQuantity(req.getQuantity());
        String requestId = normalizeRequestId(req.getRequestId());

        Optional<StockInOrder> existing = stockInOrderRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return duplicateOf(existing.get());
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return txService.doStockIn(req, requestId, operator);
            } catch (DataIntegrityViolationException e) {
                Optional<StockInOrder> done = stockInOrderRepository.findByRequestId(requestId);
                if (done.isPresent()) {
                    return duplicateOf(done.get());
                }
                if (isOrderNoConflict(e) && attempt < MAX_ATTEMPTS) {
                    continue;
                }
                log.warn("入库数据完整性错误，事务已回滚：{}", rootMessage(e));
                throw new BusinessException("DATA_INTEGRITY", "数据校验失败，操作已整体回滚");
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("入库并发冲突重试 {} 次仍失败：{}", attempt, rootMessage(e));
                    throw new BusinessException("CONCURRENT_CONFLICT", "系统繁忙（并发冲突），请稍后重试");
                }
                backoff(attempt);
            }
        }
    }

    // ------------------------------------------------------------------ 工具方法

    private static void validateQuantity(Integer qty) {
        // 服务层再次校验（不能只依赖前端/控制器校验）
        if (qty == null || qty <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "数量必须为正整数");
        }
        if (qty > 1_000_000) {
            throw new BusinessException("VALIDATION_ERROR", "单次数量不能超过 1000000");
        }
    }

    private static String normalizeRequestId(String requestId) {
        // 未传幂等号时生成随机值：此时该请求不具备幂等保护（REST 调用方应自行传入）
        return StringUtils.hasText(requestId) ? requestId.trim() : UUID.randomUUID().toString();
    }

    private static boolean isOrderNoConflict(DataIntegrityViolationException e) {
        String msg = rootMessage(e);
        return msg != null && (msg.contains("uk_out_order_no") || msg.contains("uk_in_order_no")
                || msg.contains("uk_flow_ref_order"));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(5L * attempt + (long) (Math.random() * 10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static StockOperationResult duplicateOf(StockOutOrder o) {
        return new StockOperationResult(ChangeType.OUT, o.getOrderNo(), o.getRequestId(), o.getProduct().getId(),
                o.getQuantity(), o.getBeforeStock(), o.getAfterStock(), true);
    }

    private static StockOperationResult duplicateOf(StockInOrder o) {
        return new StockOperationResult(ChangeType.IN, o.getOrderNo(), o.getRequestId(), o.getProduct().getId(),
                o.getQuantity(), o.getBeforeStock(), o.getAfterStock(), true);
    }
}
