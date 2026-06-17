package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO de respuesta con la lista de productos activos cuyo stock actual es exactamente cero,
 * incluyendo el total de productos en situación crítica y el detalle de cada uno.
 */
@Schema(description = "Productos activos con stock en cero")
public record CriticalStockResponse(
    @Schema(description = "Total de productos sin stock", example = "3") int count,
    @Schema(description = "Detalle de cada producto sin stock") List<LowStockItemDto> products) {}
