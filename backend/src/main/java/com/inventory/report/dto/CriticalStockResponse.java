package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Productos activos con stock en cero")
public record CriticalStockResponse(
    @Schema(description = "Total de productos sin stock", example = "3") int totalCritical,
    @Schema(description = "Detalle de cada producto sin stock") List<LowStockItemDto> items) {}
