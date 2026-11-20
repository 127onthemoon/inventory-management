package com.cumt.inventory.dto;

import com.cumt.inventory.entity.Product;
import com.cumt.inventory.entity.StockFlow;

import java.util.List;

/** 仪表盘统计数据 */
public record DashboardStats(
        long productCount,
        long totalStock,
        long lowStockCount,
        long todayInQuantity,
        long todayOutQuantity,
        long weekInQuantity,
        long weekOutQuantity,
        long weekInOrders,
        long weekOutOrders,
        List<Product> lowStockProducts,
        List<DailyStat> daily,
        List<StockFlow> recentFlows) {

    /** 单日入/出库统计，maxValue 用于前端画柱状图比例 */
    public record DailyStat(String date, long inQuantity, long outQuantity, int inPercent, int outPercent) {
    }
}
