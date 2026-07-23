package com.inventory.stock.event;

import com.inventory.stock.domain.StockMovement.MovementType;

/**
 * Evento de dominio que se publica al registrar un movimiento de stock. Se consume tras el commit,
 * de modo que las métricas de negocio solo cuentan movimientos que realmente se persistieron.
 */
public record StockMovementRecordedEvent(
    Long productId, String sku, MovementType type, int quantity) {}
