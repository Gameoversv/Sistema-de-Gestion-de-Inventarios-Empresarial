package com.inventory.stock.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockMovementRequest(
    @NotNull Long productId,
    @NotNull MovementType type,
    @NotNull @Min(0) Integer quantity,
    @Size(max = 500) String reason,
    String referenceId) {}
