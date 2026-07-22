package com.inventory.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de salida con los datos completos de un producto, incluyendo id, SKU, nombre, precio, niveles
 * de stock, estado activo, categoría y marcas de tiempo de auditoría.
 */
@Schema(description = "Datos del producto")
public record ProductResponse(
    @Schema(description = "ID del producto", example = "1") Long id,
    @Schema(description = "Código único de producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String name,
    @Schema(
            description = "Descripción del producto",
            example = "Intel Core i7-13700H, 16GB RAM, 512GB SSD")
        String description,
    @Schema(description = "Precio unitario", example = "1299.99") BigDecimal price,
    @Schema(description = "Cantidad en stock", example = "10") Integer stock,
    @Schema(description = "Stock mínimo configurado", example = "2") Integer minimumStock,
    @Schema(description = "Si el producto está activo", example = "true") Boolean active,
    @Schema(description = "ID de la categoría", example = "1") Long categoryId,
    @Schema(description = "Nombre de la categoría", example = "Electrónica") String categoryName,
    @Schema(description = "Fecha de creación") Instant createdAt,
    @Schema(description = "Última modificación") Instant updatedAt) {}
