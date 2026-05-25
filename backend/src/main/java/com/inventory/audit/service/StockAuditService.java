package com.inventory.audit.service;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.AuditRevisionResponse;
import com.inventory.stock.domain.StockMovement;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockAuditService {

  private final EntityManager entityManager;

  public List<AuditRevisionResponse> findMovementHistory(
      Long productId, String username, Instant from, Instant to) {

    var reader = AuditReaderFactory.get(entityManager);
    var query = reader.createQuery().forRevisionsOfEntity(StockMovement.class, false, true);

    if (productId != null) {
      query.add(AuditEntity.relatedId("product").eq(productId));
    }
    if (username != null && !username.isBlank()) {
      query.add(AuditEntity.revisionProperty("username").eq(username));
    }
    if (from != null) {
      query.add(AuditEntity.revisionProperty("revtstmp").ge(from.toEpochMilli()));
    }
    if (to != null) {
      query.add(AuditEntity.revisionProperty("revtstmp").le(to.toEpochMilli()));
    }

    query.addOrder(AuditEntity.revisionNumber().desc());

    @SuppressWarnings("unchecked")
    List<Object[]> results = query.getResultList();

    return results.stream().map(this::toResponse).toList();
  }

  private AuditRevisionResponse toResponse(Object[] row) {
    StockMovement movement = (StockMovement) row[0];
    RevisionInfo revision = (RevisionInfo) row[1];
    RevisionType revisionType = (RevisionType) row[2];

    String sku = null;
    String productName = null;
    Long productId = null;
    try {
      if (movement.getProduct() != null) {
        sku = movement.getProduct().getSku();
        productName = movement.getProduct().getName();
        productId = movement.getProduct().getId();
      }
    } catch (Exception ignored) {
      // lazy-load may fail outside of a fully-open session; fields remain null
    }

    return new AuditRevisionResponse(
        revision.getRev(),
        Instant.ofEpochMilli(revision.getRevtstmp()),
        revision.getUsername(),
        revisionType,
        movement.getId(),
        productId,
        sku,
        productName,
        movement.getType(),
        movement.getQuantity(),
        movement.getQuantityBefore(),
        movement.getQuantityAfter(),
        movement.getPerformedBy(),
        movement.getReason());
  }
}
