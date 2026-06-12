package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Métricas generales del inventario para el dashboard principal")
public record DashboardMetricsResponse(
    @Schema(description = "Total de productos registrados", example = "120") int totalProducts,
    @Schema(description = "Productos activos", example = "115") int activeProducts,
    @Schema(description = "Productos inactivos", example = "5") int inactiveProducts,
    @Schema(description = "Total de categorías", example = "8") long totalCategories,
    @Schema(description = "Total de movimientos de stock registrados", example = "1450")
        long totalStockMovements,
    @Schema(
            description = "Valor total del inventario activo (precio × stock)",
            example = "750000.00")
        BigDecimal totalInventoryValue,
    @Schema(description = "Productos con stock ≤ minimumStock", example = "12") int lowStockCount,
    @Schema(description = "Productos con stock = 0", example = "3") int criticalStockCount,
    @Schema(description = "Fecha y hora del último movimiento registrado")
        Instant lastMovementAt) {}
