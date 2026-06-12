package com.inventory.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductPatchRequest(
    @Size(max = 100) String sku,
    @Size(max = 255) String name,
    String description,
    @DecimalMin("0.00") BigDecimal price,
    @Min(0) Integer stock,
    @Min(0) Integer minimumStock,
    Boolean active,
    Long categoryId) {}
