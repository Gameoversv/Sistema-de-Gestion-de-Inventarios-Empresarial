package com.inventory.stock.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;

public record StockMovementResponse(
    Long id,
    Long productId,
    String sku,
    String productName,
    MovementType type,
    Integer quantity,
    Integer quantityBefore,
    Integer quantityAfter,
    String reason,
    String referenceId,
    String performedBy,
    Instant createdAt) {}
