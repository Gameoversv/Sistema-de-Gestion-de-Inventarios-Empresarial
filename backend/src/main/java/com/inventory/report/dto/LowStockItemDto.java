package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO con los datos de un producto cuyo stock está por debajo del mínimo configurado. Incluye id,
 * SKU, nombre, stock actual, mínimo requerido, déficit calculado y categoría.
 */
@Schema(description = "Producto con stock por debajo del mínimo")
public record LowStockItemDto(
    @Schema(description = "ID del producto", example = "42") Long id,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String name,
    @Schema(description = "Stock actual", example = "1") int currentStock,
    @Schema(description = "Stock mínimo configurado", example = "5") int minimumStock,
    @Schema(description = "Déficit (minimumStock − currentStock)", example = "4") int deficit,
    @Schema(description = "Categoría del producto", example = "Electrónica") String categoryName) {}
