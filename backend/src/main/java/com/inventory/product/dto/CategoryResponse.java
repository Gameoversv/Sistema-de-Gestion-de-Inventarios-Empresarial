package com.inventory.product.dto;

import java.time.Instant;

public record CategoryResponse(
    Long id, String name, String description, Instant createdAt, Instant updatedAt) {}
