package com.inventory.audit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.envers.RevisionType;

/**
 * DTO de respuesta para revisiones de auditoría Envers sobre la entidad {@code Product}. Captura el
 * estado del producto (SKU, nombre, precio, stock, categoría) en cada revisión.
 */
public record ProductAuditResponse(
    int revisionNumber,
    Instant revisionTimestamp,
    String revisedBy,
    RevisionType revisionType,
    Long productId,
    String sku,
    String name,
    BigDecimal price,
    Integer stock,
    Integer minimumStock,
    Boolean active,
    String categoryName) {}
