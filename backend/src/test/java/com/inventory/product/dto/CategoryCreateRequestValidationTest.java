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

  @Test
  @DisplayName("request válido no produce violaciones")
  void validRequest_noViolations() {
    CategoryCreateRequest request = new CategoryCreateRequest("Electronics", "Electronic items");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("name nulo produce violación")
  void nullName_violatesNotBlank() {
    CategoryCreateRequest request = new CategoryCreateRequest(null, "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("name en blanco produce violación")
  void blankName_violatesNotBlank() {
    CategoryCreateRequest request = new CategoryCreateRequest("   ", "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("name mayor a 100 caracteres produce violación de tamaño")
  void nameExceeds100Chars_violatesSize() {
    CategoryCreateRequest request = new CategoryCreateRequest("C".repeat(101), "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("description nula es permitida (campo opcional)")
  void nullDescription_isValid() {
    CategoryCreateRequest request = new CategoryCreateRequest("Electronics", null);

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("name exactamente 100 caracteres es válido")
  void nameExactly100Chars_isValid() {
    CategoryCreateRequest request = new CategoryCreateRequest("C".repeat(100), "desc");

    Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("name"));
  }
}
