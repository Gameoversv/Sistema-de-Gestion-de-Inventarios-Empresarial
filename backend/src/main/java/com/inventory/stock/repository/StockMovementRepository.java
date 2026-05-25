package com.inventory.stock.repository;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  Page<StockMovement> findByProductId(Long productId, Pageable pageable);

  List<StockMovement> findByProductIdAndType(Long productId, MovementType type);

  List<StockMovement> findByCreatedAtBetween(Instant from, Instant to);

  @Query(
      """
      SELECT m FROM StockMovement m
      WHERE (:productId IS NULL OR m.product.id = :productId)
        AND (:type     IS NULL OR m.type = :type)
        AND (:from     IS NULL OR m.createdAt >= :from)
        AND (:to       IS NULL OR m.createdAt <= :to)
      ORDER BY m.createdAt DESC
      """)
  Page<StockMovement> findFiltered(
      @Param("productId") Long productId,
      @Param("type") MovementType type,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
