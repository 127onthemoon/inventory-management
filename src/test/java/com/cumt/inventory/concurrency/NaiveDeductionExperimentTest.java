package com.cumt.inventory.concurrency;

import com.cumt.inventory.entity.Product;
import com.cumt.inventory.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【反例对照实验】演示“先查库存 → Java 内存计算 → 写回”的错误写法在并发下会出现超卖/丢失更新。
 * <p>
 * 本类中的错误写法只存在于测试代码里，用于在课程报告中与正确方案做对比；业务代码中绝不使用。
 * 由于并发交错具有随机性，本实验只断言“正确方案的结果恒定正确”，错误写法的结果如实打印。
 */
class NaiveDeductionExperimentTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("反例：读-改-写（无原子条件更新）在 100 并发抢 50 时的表现")
    void naiveReadModifyWrite() throws Exception {
        Product p = createProduct(50);
        int n = 100;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                // ❌ 错误写法：先读
                Integer stock = jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, p.getId());
                if (stock != null && stock >= 1) {
                    Thread.sleep(1); // 模拟业务处理耗时，放大“检查-写入”时间窗口
                    // ❌ 在 Java 内存中计算后写回：多个线程可能读到同一个旧值
                    jdbc.update("UPDATE product SET stock = ? WHERE id = ?", stock - 1, p.getId());
                    sold.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        int finalStock = stockOf(p);
        System.out.printf("[EXPERIMENT] 错误写法(读-改-写) 100并发抢50 | 认为卖出=%d 最终库存=%d | 实际应扣减=%d 而库存只减少了=%d → %s%n",
                sold.get(), finalStock, sold.get(), 50 - finalStock,
                sold.get() > 50 - finalStock ? "发生超卖/丢失更新" : "本次未观察到异常（时序随机）");

        // 对照组：正确方案（原子条件更新）在同样条件下
        Product q = createProduct(50);
        ExecutorService pool2 = Executors.newFixedThreadPool(n);
        CountDownLatch start2 = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs2 = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs2.add(pool2.submit(() -> {
                start2.await();
                int rows = jdbc.update("UPDATE product SET stock = stock - 1 WHERE id = ? AND stock >= 1", q.getId());
                if (rows == 1) ok.incrementAndGet();
                return null;
            }));
        }
        start2.countDown();
        for (Future<?> f : fs2) f.get(60, TimeUnit.SECONDS);
        pool2.shutdown();
        System.out.printf("[EXPERIMENT] 正确写法(原子条件更新) 100并发抢50 | 成功=%d 最终库存=%d%n", ok.get(), stockOf(q));
        assertThat(ok.get()).isEqualTo(50);
        assertThat(stockOf(q)).isZero();
    }
}
