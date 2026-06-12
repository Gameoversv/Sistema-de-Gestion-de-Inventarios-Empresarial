package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Lista de los movimientos de stock más recientes")
public record RecentMovementsResponse(
    @Schema(description = "Límite solicitado", example = "20") int limit,
    @Schema(description = "Cantidad de movimientos devueltos", example = "20") int count,
    @Schema(description = "Movimientos ordenados de más reciente a más antiguo")
        List<RecentMovementDto> movements) {}
