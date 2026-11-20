package com.cumt.inventory;

import com.cumt.inventory.common.OrderNoGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** 单元测试：单据号生成器 */
class OrderNoGeneratorTest {

    @Test
    @DisplayName("单据号格式：前缀 + 17 位时间戳 + 4 位序号")
    void format() {
        String no = OrderNoGenerator.next("OUT");
        assertThat(no).startsWith("OUT").hasSize(3 + 17 + 4).matches("OUT\\d{21}");
    }

    @Test
    @DisplayName("多线程并发生成 20000 个单据号无重复")
    void uniqueUnderConcurrency() throws Exception {
        Set<String> set = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 2500; i++) {
                    set.add(OrderNoGenerator.next("IN"));
                }
                done.countDown();
            });
        }
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(set).hasSize(20000);
    }
}
