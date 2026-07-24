package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Respuesta del ranking de productos más vendidos. */
@Schema(description = "Ranking de productos más vendidos por unidades de salida")
public record BestSellersResponse(
    @Schema(description = "Límite aplicado a la consulta", example = "10") int limit,
    @Schema(description = "Número de productos devueltos", example = "8") int count,
    @Schema(description = "Productos ordenados por unidades vendidas, de mayor a menor")
        List<BestSellerDto> products) {}
