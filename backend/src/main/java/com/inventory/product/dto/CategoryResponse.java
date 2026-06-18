package com.inventory.product.dto;

import java.time.Instant;

/**
 * DTO de salida con los datos completos de una categoría: identificador, nombre, descripción y
 * marcas de tiempo de creación y última modificación.
 */
public record CategoryResponse(
    Long id, String name, String description, Instant createdAt, Instant updatedAt) {}
