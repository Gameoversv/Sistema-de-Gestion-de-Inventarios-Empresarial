package com.inventory.stock.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * DTO de salida con el resultado completo de un movimiento de inventario registrado. Incluye id del
 * movimiento, datos del producto, tipo, cantidades (antes/después), motivo, referencia documental,
 * usuario y timestamp de creación.
 */
@Schema(description = "Resultado del movimiento de inventario registrado")
public record StockMovementResponse(
    @Schema(description = "ID del movimiento", example = "100") Long id,
    @Schema(description = "ID del producto", example = "1") Long productId,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String productName,
    @Schema(description = "Tipo de movimiento", example = "IN") MovementType type,
    @Schema(description = "Cantidad del movimiento", example = "50") Integer quantity,
    @Schema(description = "Stock antes del movimiento", example = "10") Integer quantityBefore,
    @Schema(description = "Stock después del movimiento", example = "60") Integer quantityAfter,
    @Schema(description = "Motivo del movimiento", example = "Reposición mensual") String reason,
    @Schema(description = "Referencia del documento", example = "PO-2024-001") String referenceId,
    @Schema(description = "Usuario que realizó el movimiento", example = "admin")
        String performedBy,
    @Schema(description = "Fecha y hora del movimiento") Instant createdAt) {}
