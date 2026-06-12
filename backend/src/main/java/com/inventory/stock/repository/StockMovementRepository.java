package com.inventory.stock.repository;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository
    extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

  Page<StockMovement> findByProductId(Long productId, Pageable pageable);

  List<StockMovement> findByProductIdAndType(Long productId, MovementType type);

  List<StockMovement> findByCreatedAtBetween(Instant from, Instant to);

  Optional<StockMovement> findFirstByOrderByCreatedAtDesc();

  @Query("SELECT m FROM StockMovement m JOIN FETCH m.product p ORDER BY m.createdAt DESC")
  List<StockMovement> findRecent(Pageable pageable);
}
