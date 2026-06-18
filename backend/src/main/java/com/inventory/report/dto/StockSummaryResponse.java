package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO con el resumen global del inventario: totales de productos, productos activos, productos con
 * stock bajo, valor total monetario y desglose de stock por categoría.
 */
@Schema(description = "Resumen de niveles de stock del inventario")
public record StockSummaryResponse(
    @Schema(description = "Total de productos registrados", example = "120") int totalProducts,
    @Schema(description = "Productos activos", example = "115") int activeProducts,
    @Schema(description = "Productos bajo stock mínimo", example = "8") int lowStockProducts,
    @Schema(
            description = "Valor total del inventario activo (precio × stock)",
            example = "250000.00")
        BigDecimal totalInventoryValue,
    @Schema(description = "Desglose por categoría") List<CategoryStockDto> byCategory) {}
