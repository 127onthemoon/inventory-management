package com.cumt.inventory.concurrency;

import com.cumt.inventory.common.InsufficientStockException;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发测试（真实 MySQL + 多线程）：验证防超卖、防负库存、防重复扣减、库存与流水一致。
 * <p>
 * 所有线程先在 CountDownLatch 上等待，再同时放行，尽可能制造“同一时刻争抢同一行”的场景。
 * 线程数 > 连接池大小（50）时，多余线程在连接池排队，数据库层面始终有大量事务同时竞争同一行锁。
 */
class ConcurrentStockTest extends AbstractMySqlIntegrationTest {

    /** 并发执行结果统计 */
    record Outcome(int success, int insufficient, int duplicate, int otherError, long elapsedMs,
                   List<StockOperationResult> results, List<Throwable> errors) {
    }

    /** 让 n 个任务同时开始执行并统计结果 */
    private Outcome runConcurrently(int n, IntFunction<StockOperationResult> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        List<StockOperationResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    StockOperationResult r = task.apply(idx);
                    results.add(r);
                    if (r.duplicate()) duplicate.incrementAndGet(); else success.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Throwable e) {
                    other.incrementAndGet();
                    errors.add(e);
                }
                return null;
            }));
        }
        ready.await();
        long t0 = System.nanoTime();
        start.countDown();
        for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        return new Outcome(success.get(), insufficient.get(), duplicate.get(), other.get(), elapsed, results, errors);
    }

    private StockOutRequest outReq(Product p, int qty) {
        return new StockOutRequest(p.getId(), qty, UUID.randomUUID().toString());
    }

    /** 断言：库存 = Σ入库 − Σ出库、流水条数 = 单据数、流水链连续、无负数 */
    private ConsistencyReport.Item assertConsistent(Product p) {
        ConsistencyReport.Item item = statisticsService.check(productRepository.findById(p.getId()).orElseThrow());
        List<StockFlow> flows = stockFlowRepository.findByProductIdOrderByIdAsc(p.getId());
        assertThat(flows).allMatch(f -> f.getAfterStock() >= 0 && f.getBeforeStock() >= 0);
        assertThat(item.flowNet()).as("库存 = Σ入库 − Σ出库").isEqualTo(item.stock());
        assertThat(item.flowCount()).as("流水条数 = 单据数").isEqualTo(item.inOrderCount() + item.outOrderCount());
        assertThat(item.chainOk()).as("流水链连续").isTrue();
        assertThat(item.consistent()).isTrue();
        return item;
    }

    private static void print(String name, Outcome o, int finalStock, ConsistencyReport.Item item) {
        System.out.printf("[CONCURRENCY] %s | 成功=%d 库存不足拒绝=%d 重复提交=%d 其他错误=%d | 最终库存=%d | " +
                        "流水条数=%d 流水净变动=%d 入库单=%d 出库单=%d 流水链=%s | 耗时=%dms%n",
                name, o.success(), o.insufficient(), o.duplicate(), o.otherError(), finalStock,
                item.flowCount(), item.flowNet(), item.inOrderCount(), item.outOrderCount(),
                item.chainOk() ? "连续" : "断裂", o.elapsedMs());
        o.errors().stream().limit(3).forEach(e -> System.out.println("[CONCURRENCY]   其他错误示例：" + e));
    }

    @Test
    @DisplayName("TC-C01：初始库存 50，100 个并发请求各出库 1 → 成功恰为 50，库存 0，流水一致")
    void hundredConcurrentRequestsOnFifty() throws Exception {
        Product p = createProduct(50);
        Outcome o = runConcurrently(100, i -> stockService.stockOut(outReq(p, 1), "c-user-" + i));
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C01 100并发抢50", o, finalStock, item);

        assertThat(o.otherError()).isZero();
        assertThat(o.success()).isEqualTo(50);
        assertThat(o.insufficient()).isEqualTo(50);
        assertThat(finalStock).isZero();
        assertThat(stockOutOrderRepository.countByProductId(p.getId())).isEqualTo(50);
        assertThat(item.flowCount()).isEqualTo(51); // 1 条期初入库 + 50 条出库
        // 每个成功请求看到的“变动后库存”互不相同，恰好覆盖 0..49
        assertThat(o.results().stream().map(StockOperationResult::afterStock).sorted().toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 50).boxed().toList());
    }

    @Test
    @DisplayName("TC-C02：500 个并发请求争抢 50 件库存 → 成功恰为 50，库存 0，无负库存")
    void fiveHundredConcurrentRequestsOnFifty() throws Exception {
        Product p = createProduct(50);
        Outcome o = runConcurrently(500, i -> stockService.stockOut(outReq(p, 1), "c-user-" + i));
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C02 500并发抢50", o, finalStock, item);

        assertThat(o.otherError()).isZero();
        assertThat(o.success()).isEqualTo(50);
        assertThat(o.insufficient()).isEqualTo(450);
        assertThat(finalStock).isZero();
        assertThat(item.outOrderCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("TC-C03：每次出库 3 件，60 个并发抢 50 件 → 成功 16 次（48 件），剩余 2，不出现部分扣减")
    void multiQuantityConcurrent() throws Exception {
        Product p = createProduct(50);
        Outcome o = runConcurrently(60, i -> stockService.stockOut(outReq(p, 3), "c-user-" + i));
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C03 60并发×3件抢50", o, finalStock, item);

        assertThat(o.otherError()).isZero();
        assertThat(o.success()).isEqualTo(16);
        assertThat(finalStock).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-C04：同一幂等号并发提交 100 次 → 只扣减 1 次，只有 1 张单据、1 条出库流水")
    void sameRequestIdConcurrent() throws Exception {
        Product p = createProduct(50);
        String rid = "same-" + UUID.randomUUID();
        Outcome o = runConcurrently(100, i -> stockService.stockOut(new StockOutRequest(p.getId(), 1, rid), "c-user-" + i));
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C04 同一幂等号100并发", o, finalStock, item);

        assertThat(o.otherError()).isZero();
        assertThat(o.insufficient()).isZero();
        assertThat(o.success()).isEqualTo(1);
        assertThat(o.duplicate()).isEqualTo(99);
        assertThat(finalStock).isEqualTo(49);
        assertThat(item.outOrderCount()).isEqualTo(1);
        assertThat(o.results().stream().map(StockOperationResult::orderNo).distinct().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-C05：10 个幂等号 × 每个并发提交 20 次（共 200 请求）→ 恰好扣减 10 次")
    void manyRequestIdsWithDuplicates() throws Exception {
        Product p = createProduct(100);
        String[] ids = new String[10];
        for (int i = 0; i < ids.length; i++) ids[i] = "multi-" + i + "-" + UUID.randomUUID();
        Outcome o = runConcurrently(200, i -> stockService.stockOut(new StockOutRequest(p.getId(), 2, ids[i % 10]), "u" + i));
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C05 10幂等号×20重复", o, finalStock, item);

        assertThat(o.otherError()).isZero();
        assertThat(o.success()).isEqualTo(10);
        assertThat(o.duplicate()).isEqualTo(190);
        assertThat(finalStock).isEqualTo(100 - 10 * 2);
    }

    @Test
    @DisplayName("TC-C06：并发入库 + 出库混合 → 库存 = 初始 + 入库总量 − 出库总量，且与流水一致")
    void mixedInAndOutConcurrent() throws Exception {
        int initial = 20; // 初始库存较小，使部分出库因库存不足被拒绝，场景更真实
        Product p = createProduct(initial);
        Random rnd = new Random(20261115L);
        int n = 300;
        int[] qty = new int[n];
        boolean[] isIn = new boolean[n];
        for (int i = 0; i < n; i++) {
            qty[i] = 1 + rnd.nextInt(5);
            isIn[i] = rnd.nextBoolean();
        }
        Outcome o = runConcurrently(n, i -> {
            if (isIn[i]) {
                StockInRequest in = new StockInRequest();
                in.setProductId(p.getId());
                in.setQuantity(qty[i]);
                in.setRequestId(UUID.randomUUID().toString());
                return stockService.stockIn(in, "mix-" + i);
            }
            return stockService.stockOut(outReq(p, qty[i]), "mix-" + i);
        });
        int finalStock = stockOf(p);
        ConsistencyReport.Item item = assertConsistent(p);
        print("TC-C06 300并发入库+出库混合", o, finalStock, item);

        int inTotal = o.results().stream().filter(r -> r.type() == ChangeType.IN).mapToInt(StockOperationResult::quantity).sum();
        int outTotal = o.results().stream().filter(r -> r.type() == ChangeType.OUT).mapToInt(StockOperationResult::quantity).sum();
        System.out.printf("[CONCURRENCY] TC-C06 明细 | 初始=%d 入库成功总量=%d 出库成功总量=%d 期望库存=%d 实际库存=%d%n",
                initial, inTotal, outTotal, initial + inTotal - outTotal, finalStock);

        assertThat(o.otherError()).isZero();
        assertThat(finalStock).isEqualTo(initial + inTotal - outTotal);
        assertThat(finalStock).isGreaterThanOrEqualTo(0);
    }
}
