package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.InsufficientStockException;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.entity.StockOutOrder;
import com.cumt.inventory.repository.StockInOrderRepository;
import com.cumt.inventory.repository.StockOutOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 单元测试（Mockito，不连数据库）：验证门面服务的参数校验、幂等快速路径与并发冲突重试逻辑。
 */
class StockServiceUnitTest {

    private StockTxService tx;
    private StockOutOrderRepository outRepo;
    private StockService service;

    @BeforeEach
    void setUp() {
        tx = mock(StockTxService.class);
        outRepo = mock(StockOutOrderRepository.class);
        service = new StockService(tx, mock(StockInOrderRepository.class), outRepo);
        when(outRepo.findByRequestId(anyString())).thenReturn(Optional.empty());
    }

    private static StockOperationResult ok(String rid) {
        return new StockOperationResult(ChangeType.OUT, "OUT1", rid, 1L, 1, 10, 9, false);
    }

    @Test
    @DisplayName("数量 ≤ 0 直接拒绝，不进入事务")
    void rejectNonPositiveQuantity() {
        assertThatThrownBy(() -> service.stockOut(new StockOutRequest(1L, 0, "r"), "u"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("正整数");
        assertThatThrownBy(() -> service.stockOut(new StockOutRequest(1L, -5, "r"), "u"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(tx);
    }

    @Test
    @DisplayName("幂等快速路径：已处理过的 requestId 直接返回首次结果，不再扣减")
    void duplicateFastPath() {
        StockOutOrder existing = mock(StockOutOrder.class);
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(1L);
        when(existing.getProduct()).thenReturn(p);
        when(existing.getOrderNo()).thenReturn("OUT-OLD");
        when(existing.getRequestId()).thenReturn("dup");
        when(existing.getQuantity()).thenReturn(1);
        when(existing.getBeforeStock()).thenReturn(10);
        when(existing.getAfterStock()).thenReturn(9);
        when(outRepo.findByRequestId("dup")).thenReturn(Optional.of(existing));

        StockOperationResult r = service.stockOut(new StockOutRequest(1L, 1, "dup"), "u");
        assertThat(r.duplicate()).isTrue();
        assertThat(r.orderNo()).isEqualTo("OUT-OLD");
        verifyNoInteractions(tx);
    }

    @Test
    @DisplayName("死锁/锁等待超时：自动重试，第 3 次成功")
    void retryOnLockFailure() {
        when(tx.doStockOut(any(), anyString(), anyString()))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenThrow(new CannotAcquireLockException("deadlock"))
                .thenReturn(ok("r1"));
        StockOperationResult r = service.stockOut(new StockOutRequest(1L, 1, "r1"), "u");
        assertThat(r.duplicate()).isFalse();
        verify(tx, times(3)).doStockOut(any(), eq("r1"), eq("u"));
    }

    @Test
    @DisplayName("重试 3 次仍冲突：抛出 CONCURRENT_CONFLICT")
    void retryExhausted() {
        when(tx.doStockOut(any(), anyString(), anyString())).thenThrow(new CannotAcquireLockException("deadlock"));
        assertThatThrownBy(() -> service.stockOut(new StockOutRequest(1L, 1, "r2"), "u"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CONCURRENT_CONFLICT");
        verify(tx, times(StockService.MAX_ATTEMPTS)).doStockOut(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("库存不足且非重复提交：原样抛出 InsufficientStockException")
    void insufficientPropagates() {
        when(tx.doStockOut(any(), anyString(), anyString())).thenThrow(new InsufficientStockException("A", 5));
        assertThatThrownBy(() -> service.stockOut(new StockOutRequest(1L, 5, "r3"), "u"))
                .isInstanceOf(InsufficientStockException.class);
        verify(tx, times(1)).doStockOut(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("非单据号冲突的完整性错误：不重试，转换为 DATA_INTEGRITY 业务异常")
    void integrityErrorNotRetried() {
        when(tx.doStockOut(any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("Data too long for column 'remark'"));
        assertThatThrownBy(() -> service.stockOut(new StockOutRequest(1L, 1, "r4"), "u"))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("DATA_INTEGRITY");
        verify(tx, times(1)).doStockOut(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("未传 requestId 时服务端自动生成，不会报错")
    void generateRequestIdWhenMissing() {
        when(tx.doStockOut(any(), anyString(), anyString())).thenAnswer(inv -> ok(inv.getArgument(1)));
        StockOperationResult r = service.stockOut(new StockOutRequest(1L, 1, null), "u");
        assertThat(r.requestId()).isNotBlank().hasSize(36);
    }
}
