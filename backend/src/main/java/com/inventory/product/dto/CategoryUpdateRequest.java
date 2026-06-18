package com.inventory.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para actualizar una categoría existente. Comparte la misma estructura que {@link
 * CategoryCreateRequest} pero se usa en operaciones PUT sobre una categoría ya creada.
 */
public record CategoryUpdateRequest(@NotBlank @Size(max = 100) String name, String description) {}
