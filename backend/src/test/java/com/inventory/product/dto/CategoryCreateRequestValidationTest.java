package com.inventory.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryCreateRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // Verifica que un request con nombre y descripción válidos no genera violaciones de constrains.
  @Test
  @DisplayName("request válido no produce violaciones")
  void validRequest_noViolations() {
    CategoryCreateRequest request = new CategoryCreateRequest("Electronics", "Electronic items");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  // Verifica que un nombre nulo genera una violación en el campo name.
  @Test
  @DisplayName("name nulo produce violación")
  void nullName_violatesNotBlank() {
    CategoryCreateRequest request = new CategoryCreateRequest(null, "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  // Verifica que un nombre en blanco genera una violación en el campo name.
  @Test
  @DisplayName("name en blanco produce violación")
  void blankName_violatesNotBlank() {
    CategoryCreateRequest request = new CategoryCreateRequest("   ", "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  // Verifica que un nombre mayor a 100 caracteres genera una violación de tamaño en name.
  @Test
  @DisplayName("name mayor a 100 caracteres produce violación de tamaño")
  void nameExceeds100Chars_violatesSize() {
    CategoryCreateRequest request = new CategoryCreateRequest("C".repeat(101), "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  // Verifica que la descripción es un campo opcional y puede ser nula sin producir violaciones.
  @Test
  @DisplayName("description nula es permitida (campo opcional)")
  void nullDescription_isValid() {
    CategoryCreateRequest request = new CategoryCreateRequest("Electronics", null);

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  // Verifica que un nombre de exactamente 100 caracteres es válido (límite inclusivo).
  @Test
  @DisplayName("name exactamente 100 caracteres es válido")
  void nameExactly100Chars_isValid() {
    CategoryCreateRequest request = new CategoryCreateRequest("C".repeat(100), "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("name"));
  }
}
