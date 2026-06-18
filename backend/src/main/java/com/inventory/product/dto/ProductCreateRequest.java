package com.inventory.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO de entrada validado para crear un nuevo producto. Incluye SKU único obligatorio, nombre,
 * descripción opcional, precio, stock inicial, stock mínimo de alerta, estado activo y la categoría
 * a la que pertenece.
 */
@Schema(description = "Datos para crear un nuevo producto")
public record ProductCreateRequest(
    @Schema(description = "Código único de producto", example = "LAPTOP-001")
        @NotBlank
        @Size(max = 100)
        String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15")
        @NotBlank
        @Size(max = 255)
        String name,
    @Schema(
            description = "Descripción detallada del producto",
            example = "Intel Core i7-13700H, 16GB RAM, 512GB SSD")
        String description,
    @Schema(description = "Precio unitario", example = "1299.99") @NotNull @DecimalMin("0.00")
        BigDecimal price,
    @Schema(description = "Cantidad inicial en stock", example = "10") @NotNull @Min(0)
        Integer stock,
    @Schema(description = "Stock mínimo antes de alerta", example = "2") @Min(0)
        Integer minimumStock,
    @Schema(description = "Si el producto está activo", example = "true") Boolean active,
    @Schema(description = "ID de la categoría a la que pertenece", example = "1")
        Long categoryId) {}
