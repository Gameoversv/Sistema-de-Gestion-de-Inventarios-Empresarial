package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Reporte de productos bajo stock mínimo")
public record LowStockReportResponse(
    @Schema(description = "Umbral de stock utilizado en el filtro", example = "5") int threshold,
    @Schema(description = "Total de productos bajo el umbral", example = "8") int totalItems,
    @Schema(description = "Lista de productos con stock bajo") List<LowStockItemDto> items) {}
