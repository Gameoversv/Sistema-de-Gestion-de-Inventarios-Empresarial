package com.inventory.audit.dto;

import java.time.Instant;
import org.hibernate.envers.RevisionType;

/**
 * DTO unificado que representa cualquier evento de auditoría Envers independientemente
 * de la entidad afectada. Permite mostrar en el frontend un historial consolidado de
 * todos los cambios: productos, categorías, movimientos de stock y usuarios.
 */
public record UnifiedAuditEntry(
    int revisionNumber,
    Instant revisionTimestamp,
    String revisedBy,
    RevisionType revisionType,
    EntityType entityType,
    Long entityId,
    String summary) {

  public enum EntityType {
    PRODUCT,
    CATEGORY,
    STOCK_MOVEMENT,
    USER
  }
}
