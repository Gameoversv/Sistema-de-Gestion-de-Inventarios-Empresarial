package com.inventory.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Existencia actual de un producto. Es la respuesta de {@code GET /api/stock/{productId}}, el
 * endpoint que da uso a la mitad de "ver existencia e historial" que el permiso {@code stock:view}
 * declara pero no tenía forma de consultar sin pedir además {@code product:view}.
 */
@Schema(description = "Existencia actual de un producto")
public record ProductStockResponse(
    @Schema(description = "ID del producto", example = "1") Long productId,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String name,
    @Schema(description = "Unidades en existencia", example = "12") int stock,
    @Schema(description = "Stock mínimo configurado", example = "5") int minimumStock,
    @Schema(description = "Si la existencia está en el mínimo o por debajo", example = "false")
        boolean belowMinimum) {}
