package com.inventory.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear una nueva categoría. El nombre es obligatorio y tiene un máximo de 100
 * caracteres; la descripción es opcional.
 */
public record CategoryCreateRequest(@NotBlank @Size(max = 100) String name, String description) {}
