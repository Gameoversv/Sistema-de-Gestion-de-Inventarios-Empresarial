package com.inventory.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Producto en el ranking de más vendidos, calculado sobre los movimientos de salida.
 *
 * <p>No lleva stock ni precio a propósito: mide lo que ha salido del almacén, no lo que queda ni lo
 * que vale. Mezclar ambas cosas en el mismo DTO fue lo que hizo pasar durante meses un ranking por
 * valor de inventario por un ranking de ventas.
 */
@Schema(description = "Producto en el ranking de más vendidos (agregado de movimientos OUT)")
public record BestSellerDto(
    @Schema(description = "ID del producto", example = "1") Long id,
    @Schema(description = "SKU del producto", example = "LAPTOP-001") String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15") String name,
    @Schema(
            description = "Unidades vendidas: suma de las cantidades de los movimientos OUT",
            example = "140")
        Long unitsSold,
    @Schema(description = "Número de movimientos de salida registrados", example = "12")
        Long movementCount) {}
