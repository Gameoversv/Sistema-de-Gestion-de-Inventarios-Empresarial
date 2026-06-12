package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Producto en el ranking por valor o cantidad de stock")
public record TopProductDto(
    @Schema(description = "ID del producto", example = "1") Long id,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String name,
    @Schema(description = "Unidades en stock", example = "50") int stock,
    @Schema(description = "Precio unitario", example = "1299.99") BigDecimal price,
    @Schema(description = "Valor total en inventario (precio × stock)", example = "64999.50")
        BigDecimal inventoryValue,
    @Schema(description = "Categoría del producto", example = "Electrónica") String categoryName) {}
