package com.cumt.inventory.repository;

import com.cumt.inventory.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // =====================================================================
    // 并发控制核心：原子条件更新
    // =====================================================================

    /**
     * 【原子扣减库存】—— 防超卖的关键语句：
     * <pre>
     *   UPDATE product SET stock = stock - :qty WHERE id = :id AND stock >= :qty
     * </pre>
     * 1. “判断库存是否足够”和“扣减库存”在同一条 SQL 中完成，不存在“先查后改”的时间窗口；
     * 2. InnoDB 执行 UPDATE 时会对该行加排他锁（X 锁）并读取最新已提交版本（当前读），
     *    多个并发事务对同一商品的扣减因此被数据库串行化；
     * 3. 后到的事务拿到锁时看到的是前一个事务提交后的库存，若已不足，WHERE 条件不成立，
     *    影响行数为 0 —— 调用方据此判定“库存不足”并回滚，库存永远不会变成负数；
     * 4. 该行锁一直持有到事务提交/回滚，保证“扣库存 + 写单据 + 写流水”对其他事务原子可见。
     *
     * @return 受影响行数：1 = 扣减成功；0 = 库存不足（或商品不存在/已停用）
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock - :qty, updated_at = NOW() " +
            "WHERE id = :id AND status = 1 AND stock >= :qty", nativeQuery = true)
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 【原子增加库存】UPDATE product SET stock = stock + :qty WHERE id = :id。
     * 同样由数据库在行锁内完成“读-改-写”，与并发出库互斥，不会丢失更新。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE product SET stock = stock + :qty, updated_at = NOW() " +
            "WHERE id = :id AND status = 1", nativeQuery = true)
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 在已持有行锁的事务内读取最新库存（当前读）。
     * 因为本事务刚刚执行过 UPDATE，已持有该行 X 锁，FOR UPDATE 不会额外等待，
     * 读到的正是“本次变动后的库存”，据此可推算变动前库存，写入单据和流水。
     */
    @Query(value = "SELECT stock FROM product WHERE id = :id FOR UPDATE", nativeQuery = true)
    Integer currentStockForUpdate(@Param("id") Long id);

    /** 普通查询（快照读），用于展示与核对 */
    @Query(value = "SELECT stock FROM product WHERE id = :id", nativeQuery = true)
    Integer currentStock(@Param("id") Long id);

    // =====================================================================
    // 普通查询
    // =====================================================================

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    long countByCategoryId(Long categoryId);

    long countBySupplierId(Long supplierId);

    @Query("SELECT p FROM Product p WHERE " +
            "(:keyword IS NULL OR p.code LIKE CONCAT('%', :keyword, '%') OR p.name LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId)")
    Page<Product> search(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, Pageable pageable);

    List<Product> findByStatusOrderByCodeAsc(Integer status);

    @Query("SELECT p FROM Product p WHERE p.stock <= p.warningThreshold ORDER BY p.stock ASC")
    List<Product> findLowStock();

    @Query("SELECT COALESCE(SUM(p.stock), 0) FROM Product p")
    long sumStock();
}
