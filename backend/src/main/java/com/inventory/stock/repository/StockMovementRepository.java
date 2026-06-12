package com.inventory.stock.repository;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  Page<StockMovement> findByProductId(Long productId, Pageable pageable);

  List<StockMovement> findByProductIdAndType(Long productId, MovementType type);

  List<StockMovement> findByCreatedAtBetween(Instant from, Instant to);
}
