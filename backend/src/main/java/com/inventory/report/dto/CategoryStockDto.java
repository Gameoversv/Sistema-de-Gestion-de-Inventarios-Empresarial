package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Stock agrupado por categoría")
public record CategoryStockDto(
    @Schema(description = "Nombre de la categoría", example = "Electrónica") String categoryName,
    @Schema(description = "Número de productos en la categoría", example = "15") long productCount,
    @Schema(description = "Unidades totales en stock", example = "230") long totalStock,
    @Schema(description = "Valor total de la categoría", example = "48500.00")
        BigDecimal totalValue) {}
