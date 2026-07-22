package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO de respuesta con los N movimientos de stock más recientes, ordenados cronológicamente de más
 * nuevo a más antiguo. Incluye el límite solicitado y la cantidad real devuelta.
 */
@Schema(description = "Lista de los movimientos de stock más recientes")
public record RecentMovementsResponse(
    @Schema(description = "Límite solicitado", example = "20") int limit,
    @Schema(description = "Cantidad de movimientos devueltos", example = "20") int count,
    @Schema(description = "Movimientos ordenados de más reciente a más antiguo")
        List<RecentMovementDto> movements) {}
