package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO de respuesta del reporte de stock bajo. Contiene el umbral aplicado en el filtro,
 * el total de productos que lo superan y el detalle de cada uno como {@link LowStockItemDto}.
 */
@Schema(description = "Reporte de productos bajo stock mínimo")
public record LowStockReportResponse(
    @Schema(description = "Umbral de stock utilizado en el filtro", example = "5") int threshold,
    @Schema(description = "Total de productos bajo el umbral", example = "8") int count,
    @Schema(description = "Lista de productos con stock bajo") List<LowStockItemDto> items) {}
