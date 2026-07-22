package com.inventory.audit.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import org.hibernate.envers.RevisionType;

/**
 * DTO de respuesta que representa una revisión de auditoría de un movimiento de stock obtenida
 * desde Hibernate Envers. Incluye datos de la revisión, el usuario que la generó y el estado del
 * movimiento en ese punto en el tiempo.
 */
public record AuditRevisionResponse(
    int revisionNumber,
    Instant revisionTimestamp,
    String revisedBy,
    RevisionType revisionType,
    Long movementId,
    Long productId,
    String sku,
    String productName,
    MovementType movementType,
    Integer quantity,
    Integer quantityBefore,
    Integer quantityAfter,
    String performedBy,
    String reason) {}
