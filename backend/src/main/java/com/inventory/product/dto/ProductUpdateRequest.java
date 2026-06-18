package com.inventory.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO de entrada para reemplazar completamente un producto (PUT). Todos los campos son
 * obligatorios, a diferencia de {@link ProductPatchRequest} que es parcial.
 */
public record ProductUpdateRequest(
    @NotBlank @Size(max = 100) String sku,
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    @NotNull @Min(0) Integer stock,
    @NotNull @Min(0) Integer minimumStock,
    @NotNull Boolean active,
    Long categoryId) {}
