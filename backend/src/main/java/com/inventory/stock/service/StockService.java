package com.inventory.stock.service;

import com.inventory.product.dto.ProductResponse;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface StockService {

  StockMovementResponse registerMovement(
      StockMovementRequest request, Authentication authentication);

  int currentStock(Long productId);

  Page<StockMovementResponse> getMovements(
      Long productId, MovementType type, Instant from, Instant to, Pageable pageable);

  List<ProductResponse> getLowStockAlerts();
}
