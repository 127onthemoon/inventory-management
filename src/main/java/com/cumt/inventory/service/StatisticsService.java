package com.cumt.inventory.service;

import com.cumt.inventory.dto.ConsistencyReport;
import com.cumt.inventory.dto.DashboardStats;
import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.entity.StockFlow;
import com.cumt.inventory.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 数据统计与库存一致性核对 */
@Service
public class StatisticsService {

    private final ProductRepository productRepository;
    private final StockInOrderRepository stockInOrderRepository;
    private final StockOutOrderRepository stockOutOrderRepository;
    private final StockFlowRepository stockFlowRepository;

    public StatisticsService(ProductRepository productRepository, StockInOrderRepository stockInOrderRepository,
                             StockOutOrderRepository stockOutOrderRepository, StockFlowRepository stockFlowRepository) {
        this.productRepository = productRepository;
        this.stockInOrderRepository = stockInOrderRepository;
        this.stockOutOrderRepository = stockOutOrderRepository;
        this.stockFlowRepository = stockFlowRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<Product> low = productRepository.findLowStock();

        // 近 7 日每日入/出库数量（没有数据的日期补 0）
        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            byDay.put(LocalDate.now().minusDays(i).toString(), new long[2]);
        }
        for (Object[] row : stockInOrderRepository.dailyQuantity(weekStart)) {
            long[] v = byDay.get(String.valueOf(row[0]));
            if (v != null) v[0] = ((Number) row[1]).longValue();
        }
        for (Object[] row : stockOutOrderRepository.dailyQuantity(weekStart)) {
            long[] v = byDay.get(String.valueOf(row[0]));
            if (v != null) v[1] = ((Number) row[1]).longValue();
        }
        long max = byDay.values().stream().mapToLong(v -> Math.max(v[0], v[1])).max().orElse(0);
        List<DashboardStats.DailyStat> daily = new ArrayList<>();
        byDay.forEach((d, v) -> daily.add(new DashboardStats.DailyStat(d.substring(5), v[0], v[1],
                percent(v[0], max), percent(v[1], max))));

        return new DashboardStats(
                productRepository.count(),
                productRepository.sumStock(),
                low.size(),
                stockInOrderRepository.sumQuantitySince(today),
                stockOutOrderRepository.sumQuantitySince(today),
                stockInOrderRepository.sumQuantitySince(weekStart),
                stockOutOrderRepository.sumQuantitySince(weekStart),
                stockInOrderRepository.countSince(weekStart),
                stockOutOrderRepository.countSince(weekStart),
                low.size() > 8 ? low.subList(0, 8) : low,
                daily,
                stockFlowRepository.findTop10ByOrderByIdDesc());
    }

    private static int percent(long v, long max) {
        if (max == 0 || v == 0) return 0;
        return (int) Math.max(3, Math.round(v * 100.0 / max));
    }

    /** 全量一致性核对（商品数量为课程级规模，逐个商品核对即可） */
    @Transactional(readOnly = true)
    public ConsistencyReport consistency() {
        List<ConsistencyReport.Item> items = new ArrayList<>();
        for (Product p : productRepository.findAll(Sort.by("id"))) {
            items.add(check(p));
        }
        long bad = items.stream().filter(i -> !i.consistent()).count();
        return new ConsistencyReport(bad == 0, items.size(), (int) bad, items);
    }

    @Transactional(readOnly = true)
    public ConsistencyReport.Item check(Product p) {
        int stock = productRepository.currentStock(p.getId());
        List<StockFlow> flows = stockFlowRepository.findByProductIdOrderByIdAsc(p.getId());
        long net = 0;
        boolean chainOk = true;
        Integer prevAfter = null;
        for (StockFlow f : flows) {
            int sign = f.getChangeType() == ChangeType.IN ? 1 : -1;
            net += (long) sign * f.getQuantity();
            if (f.getAfterStock() != f.getBeforeStock() + sign * f.getQuantity()) chainOk = false;
            if (prevAfter != null && !prevAfter.equals(f.getBeforeStock())) chainOk = false;
            if (f.getAfterStock() < 0) chainOk = false;
            prevAfter = f.getAfterStock();
        }
        if (prevAfter != null && prevAfter != stock) chainOk = false;
        long inCount = stockInOrderRepository.countByProductId(p.getId());
        long outCount = stockOutOrderRepository.countByProductId(p.getId());
        boolean consistent = stock >= 0 && net == stock && chainOk && flows.size() == inCount + outCount;
        return new ConsistencyReport.Item(p.getId(), p.getCode(), p.getName(), stock, net, flows.size(),
                inCount, outCount, chainOk, consistent);
    }
}
