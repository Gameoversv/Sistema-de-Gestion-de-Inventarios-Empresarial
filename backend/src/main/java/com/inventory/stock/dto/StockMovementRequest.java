package com.inventory.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inventory.stock.domain.StockMovement.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
        String referenceId) {

  /**
   * Una entrada o una salida de cero unidades no mueve inventario, pero sí escribe una fila en el
   * historial y suma al contador de movimientos. El {@code @Min(0)} del campo no lo distingue, así
   * que la regla se aplica aquí, donde se conoce el tipo.
   *
   * <p>En {@code ADJUSTMENT} el cero es legítimo: el ajuste no suma ni resta, fija el stock en el
   * valor indicado, y fijarlo en cero es una corrección de recuento válida.
   *
   * <p>Con {@code type} o {@code quantity} nulos la regla calla y deja que lo reporte el
   * {@code @NotNull} correspondiente, para no emitir dos mensajes sobre el mismo dato ausente.
   */
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "La cantidad debe ser mayor que cero para movimientos IN y OUT")
  public boolean isQuantityConsistentWithType() {
    if (type == null || quantity == null) {
      return true;
    }
    return type == MovementType.ADJUSTMENT || quantity > 0;
  }
}
