package com.inventory.stock.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validación condicional de {@code quantity} según el tipo de movimiento (E-2).
 *
 * <p>El {@code @Min(0)} anterior aceptaba una entrada o una salida de cero unidades: un movimiento
 * que no mueve nada, pero que sí escribe fila en el historial y dispara el contador de métricas. En
 * {@code ADJUSTMENT} el cero sí es legítimo, porque fija el stock en ese valor.
 */
class StockMovementRequestValidationTest {

  private static Validator validator;

  private static final String QUANTITY_RULE = "quantityConsistentWithType";

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // Verifica que una entrada de cero unidades es rechazada: no mueve inventario.
  @Test
  @DisplayName("IN con quantity 0 produce violación")
  void inWithZeroQuantity_violatesRule() {
    var request = new StockMovementRequest(1L, MovementType.IN, 0, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals(QUANTITY_RULE));
  }

  // Verifica que una salida de cero unidades es rechazada por la misma razón.
  @Test
  @DisplayName("OUT con quantity 0 produce violación")
  void outWithZeroQuantity_violatesRule() {
    var request = new StockMovementRequest(1L, MovementType.OUT, 0, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals(QUANTITY_RULE));
  }

  // Verifica que un ajuste a cero sí es válido: fija el stock del producto en cero.
  @Test
  @DisplayName("ADJUSTMENT con quantity 0 es válido")
  void adjustmentWithZeroQuantity_isValid() {
    var request = new StockMovementRequest(1L, MovementType.ADJUSTMENT, 0, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  // Verifica que una entrada de una unidad sigue siendo válida.
  @Test
  @DisplayName("IN con quantity 1 es válido")
  void inWithPositiveQuantity_isValid() {
    var request = new StockMovementRequest(1L, MovementType.IN, 1, "reposición", "PO-1");

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  // Verifica que la cantidad negativa la sigue rechazando @Min(0), en el propio campo.
  @Test
  @DisplayName("quantity negativa produce violación en el campo quantity")
  void negativeQuantity_violatesMin() {
    var request = new StockMovementRequest(1L, MovementType.IN, -5, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
  }

  // Verifica que con type nulo la regla condicional calla y solo reporta @NotNull, para no dar dos
  // mensajes sobre el mismo dato faltante.
  @Test
  @DisplayName("type nulo solo produce la violación de @NotNull en type")
  void nullType_onlyReportsNotNull() {
    var request = new StockMovementRequest(1L, null, 0, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("type"))
        .noneMatch(v -> v.getPropertyPath().toString().equals(QUANTITY_RULE));
  }

  // Verifica que con quantity nula la regla condicional calla y solo reporta @NotNull.
  @Test
  @DisplayName("quantity nula solo produce la violación de @NotNull en quantity")
  void nullQuantity_onlyReportsNotNull() {
    var request = new StockMovementRequest(1L, MovementType.IN, null, null, null);

    Set<ConstraintViolation<StockMovementRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"))
        .noneMatch(v -> v.getPropertyPath().toString().equals(QUANTITY_RULE));
  }
}
