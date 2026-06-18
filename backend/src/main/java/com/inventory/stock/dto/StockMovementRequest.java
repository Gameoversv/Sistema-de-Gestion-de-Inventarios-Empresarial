package com.inventory.stock.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para registrar un movimiento de stock. Requiere el producto, el tipo de movimiento
 * (IN/OUT/ADJUSTMENT), la cantidad y opcionalmente motivo y referencia documental.
 */
@Schema(description = "Datos para registrar un movimiento de inventario")
public record StockMovementRequest(
    @Schema(description = "ID del producto", example = "1") @NotNull Long productId,
    @Schema(
            description = "Tipo de movimiento: IN (entrada), OUT (salida), ADJUSTMENT (ajuste)",
            example = "IN")
        @NotNull
        MovementType type,
    @Schema(description = "Cantidad de unidades", example = "50") @NotNull @Min(0) Integer quantity,
    @Schema(description = "Motivo del movimiento", example = "Reposición mensual") @Size(max = 500)
        String reason,
    @Schema(description = "Número de referencia del pedido o documento", example = "PO-2024-001")
        String referenceId) {}
