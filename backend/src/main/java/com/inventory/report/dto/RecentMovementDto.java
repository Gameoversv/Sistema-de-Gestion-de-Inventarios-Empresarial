package com.inventory.report.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * DTO que representa un movimiento de stock reciente para el reporte del dashboard. Incluye datos
 * del movimiento y del producto asociado, incluyendo stock antes y después.
 */
@Schema(description = "Movimiento de stock reciente")
public record RecentMovementDto(
    @Schema(description = "ID del movimiento", example = "250") Long id,
    @Schema(description = "ID del producto", example = "1") Long productId,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String productName,
    @Schema(description = "Tipo de movimiento", example = "IN") MovementType type,
    @Schema(description = "Cantidad del movimiento", example = "20") int quantity,
    @Schema(description = "Stock antes del movimiento", example = "30") Integer quantityBefore,
    @Schema(description = "Stock después del movimiento", example = "50") Integer quantityAfter,
    @Schema(description = "Usuario que realizó el movimiento", example = "admin")
        String performedBy,
    @Schema(description = "Fecha y hora del movimiento") Instant createdAt) {}
