package com.cumt.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * 高并发库存管理系统 启动类。
 */
@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        // 统一使用东八区（北京时间）时间（与 MySQL default-time-zone 保持一致）
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(InventoryApplication.class, args);
    }
}
