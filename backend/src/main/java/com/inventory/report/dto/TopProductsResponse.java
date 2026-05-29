package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ranking de productos por valor de inventario o cantidad de stock")
public record TopProductsResponse(
    @Schema(description = "Límite aplicado", example = "10") int limit,
    @Schema(
            description = "Métrica de ordenamiento: value (precio×stock) | quantity (unidades)",
            example = "value")
        String metric,
    @Schema(description = "Productos ordenados de mayor a menor") List<TopProductDto> items) {}
