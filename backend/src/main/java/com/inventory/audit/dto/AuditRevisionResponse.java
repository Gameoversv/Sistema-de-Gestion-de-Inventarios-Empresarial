package com.inventory.audit.dto;

import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import org.hibernate.envers.RevisionType;

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
