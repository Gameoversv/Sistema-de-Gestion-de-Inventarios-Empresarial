package com.inventory.audit.service;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.UnifiedAuditEntry;
import com.inventory.audit.dto.UnifiedAuditEntry.EntityType;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.security.domain.AppUser;
import com.inventory.stock.domain.StockMovement;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio que consolida el historial de revisiones Envers de todas las entidades auditadas
 * (Product, Category, StockMovement, AppUser) en una lista ordenada por número de revisión
 * descendente. Permite obtener una vista unificada de todos los cambios del sistema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnifiedAuditService {

  private final EntityManager entityManager;

  public List<UnifiedAuditEntry> findAll() {
    var reader = AuditReaderFactory.get(entityManager);
    var entries = new ArrayList<UnifiedAuditEntry>();

    entries.addAll(queryEntity(reader, Product.class, EntityType.PRODUCT));
    entries.addAll(queryEntity(reader, Category.class, EntityType.CATEGORY));
    entries.addAll(queryEntity(reader, StockMovement.class, EntityType.STOCK_MOVEMENT));
    entries.addAll(queryEntity(reader, AppUser.class, EntityType.USER));

    entries.sort(Comparator.comparingInt(UnifiedAuditEntry::revisionNumber).reversed());
    return entries;
  }

  @SuppressWarnings("unchecked")
  private List<UnifiedAuditEntry> queryEntity(
      org.hibernate.envers.AuditReader reader, Class<?> entityClass, EntityType type) {
    try {
      var query = reader.createQuery().forRevisionsOfEntity(entityClass, false, true);
      List<Object[]> rows = query.getResultList();
      return rows.stream().map(row -> toEntry(row, type)).toList();
    } catch (Exception ex) {
      log.warn("Could not query audit for {}: {}", entityClass.getSimpleName(), ex.getMessage());
      return List.of();
    }
  }

  private UnifiedAuditEntry toEntry(Object[] row, EntityType type) {
    Object entity = row[0];
    RevisionInfo revision = (RevisionInfo) row[1];
    RevisionType revisionType = (RevisionType) row[2];

    long entityId = resolveId(entity);
    String summary = buildSummary(entity, type);

    return new UnifiedAuditEntry(
        revision.getRev(),
        Instant.ofEpochMilli(revision.getRevtstmp()),
        revision.getUsername(),
        revisionType,
        type,
        entityId,
        summary);
  }

  private long resolveId(Object entity) {
    if (entity instanceof Product p) return p.getId() != null ? p.getId() : 0L;
    if (entity instanceof Category c) return c.getId() != null ? c.getId() : 0L;
    if (entity instanceof StockMovement s) return s.getId() != null ? s.getId() : 0L;
    if (entity instanceof AppUser u) return u.getId() != null ? u.getId() : 0L;
    return 0L;
  }

  private String buildSummary(Object entity, EntityType type) {
    return switch (type) {
      case PRODUCT -> buildProductSummary((Product) entity);
      case CATEGORY -> buildCategorySummary((Category) entity);
      case STOCK_MOVEMENT -> buildMovementSummary((StockMovement) entity);
      case USER -> buildUserSummary((AppUser) entity);
    };
  }

  private String buildProductSummary(Product p) {
    return "%s | %s | Stock: %d | $%s"
        .formatted(p.getSku(), p.getName(), p.getStock(), p.getPrice());
  }

  private String buildCategorySummary(Category c) {
    return c.getName();
  }

  private String buildMovementSummary(StockMovement s) {
    String productInfo = "";
    try {
      if (s.getProduct() != null) {
        productInfo = " | " + s.getProduct().getSku() + " - " + s.getProduct().getName();
      }
    } catch (LazyInitializationException ignored) {
    }
    return "%s %d unid.%s".formatted(s.getType(), s.getQuantity(), productInfo);
  }

  private String buildUserSummary(AppUser u) {
    return "%s (%s) | Rol: %s"
        .formatted(u.getDisplayName() != null ? u.getDisplayName() : "", u.getEmail(), u.getRole());
  }
}
