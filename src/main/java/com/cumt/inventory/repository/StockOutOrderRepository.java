package com.cumt.inventory.repository;

import com.cumt.inventory.entity.StockOutOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockOutOrderRepository extends JpaRepository<StockOutOrder, Long> {

    /** 幂等查询：根据请求幂等号查找已存在的出库单 */
    Optional<StockOutOrder> findByRequestId(String requestId);

    long countByProductId(Long productId);

    @Query("SELECT o FROM StockOutOrder o WHERE (:productId IS NULL OR o.product.id = :productId)")
    Page<StockOutOrder> search(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM StockOutOrder o WHERE o.createdAt >= :from")
    long sumQuantitySince(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(o) FROM StockOutOrder o WHERE o.createdAt >= :from")
    long countSince(@Param("from") LocalDateTime from);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS d, SUM(quantity) FROM stock_out_order " +
            "WHERE created_at >= :from GROUP BY d ORDER BY d", nativeQuery = true)
    List<Object[]> dailyQuantity(@Param("from") LocalDateTime from);
}
