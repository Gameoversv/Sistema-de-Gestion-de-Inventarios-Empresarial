package com.inventory.stock.service;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockService {

  StockMovement register(Long productId, MovementType type, int quantity, String reason);

  int currentStock(Long productId);

  Page<StockMovement> movements(Long productId, Pageable pageable);
}
