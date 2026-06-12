package com.inventory.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductCreateRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  @DisplayName("request válido no produce violaciones")
  void validRequest_noViolations() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("sku en blanco produce violación en campo sku")
  void blankSku_violatesNotBlank() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "   ", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
  }

  @Test
  @DisplayName("sku nulo produce violación")
  void nullSku_violatesNotBlank() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            null, "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
  }

  @Test
  @DisplayName("sku mayor a 100 caracteres produce violación de tamaño")
  void skuExceeds100Chars_violatesSize() {
    String longSku = "A".repeat(101);
    ProductCreateRequest request =
        new ProductCreateRequest(
            longSku, "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
  }

  @Test
  @DisplayName("name en blanco produce violación")
  void blankName_violatesNotBlank() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("name mayor a 255 caracteres produce violación de tamaño")
  void nameExceeds255Chars_violatesSize() {
    String longName = "N".repeat(256);
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", longName, "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("price nulo produce violación")
  void nullPrice_violatesNotNull() {
    ProductCreateRequest request =
        new ProductCreateRequest("SKU-001", "Laptop", "desc", null, 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
  }

  @Test
  @DisplayName("price negativo produce violación de mínimo decimal")
  void negativePrice_violatesDecimalMin() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("-0.01"), 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
  }

  @Test
  @DisplayName("stock nulo produce violación")
  void nullStock_violatesNotNull() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), null, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stock"));
  }

  @Test
  @DisplayName("stock negativo produce violación de mínimo")
  void negativeStock_violatesMin() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), -1, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stock"));
  }

  @Test
  @DisplayName("minimumStock negativo produce violación de mínimo")
  void negativeMinimumStock_violatesMin() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, -1, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("minimumStock"));
  }

  @Test
  @DisplayName("precio cero es válido (DecimalMin inclusive)")
  void zeroPriceIsValid() {
    ProductCreateRequest request =
        new ProductCreateRequest("SKU-001", "Laptop", "desc", BigDecimal.ZERO, 10, 2, true, null);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("price"));
  }
}
