package com.cumt.inventory.repository;

import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.entity.StockFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockFlowRepository extends JpaRepository<StockFlow, Long> {

    @Query("SELECT f FROM StockFlow f WHERE (:productId IS NULL OR f.product.id = :productId) " +
            "AND (:type IS NULL OR f.changeType = :type)")
    Page<StockFlow> search(@Param("productId") Long productId, @Param("type") ChangeType type, Pageable pageable);

    long countByProductId(Long productId);

    List<StockFlow> findByProductIdOrderByIdAsc(Long productId);

    List<StockFlow> findTop10ByOrderByIdDesc();

    /** Σ入库 − Σ出库（按流水计算的理论库存） */
    @Query("SELECT COALESCE(SUM(CASE WHEN f.changeType = com.cumt.inventory.entity.ChangeType.IN " +
            "THEN f.quantity ELSE -f.quantity END), 0) FROM StockFlow f WHERE f.product.id = :productId")
    long netQuantity(@Param("productId") Long productId);
}
