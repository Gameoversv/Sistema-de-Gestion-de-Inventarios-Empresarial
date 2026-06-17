package com.inventory.audit.service;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.ProductAuditResponse;
import com.inventory.product.domain.Product;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio que consulta el historial de revisiones de productos usando Hibernate Envers.
 * Devuelve las revisiones ordenadas de más reciente a más antigua.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAuditService {

  private final EntityManager entityManager;

  public List<ProductAuditResponse> findProductHistory(String username) {
    var reader = AuditReaderFactory.get(entityManager);
    var query = reader.createQuery().forRevisionsOfEntity(Product.class, false, true);

    if (username != null && !username.isBlank()) {
      query.add(AuditEntity.revisionProperty("username").eq(username));
    }

    query.addOrder(AuditEntity.revisionNumber().desc());

    @SuppressWarnings("unchecked")
    List<Object[]> results = query.getResultList();

    return results.stream().map(this::toResponse).toList();
  }

  private ProductAuditResponse toResponse(Object[] row) {
    Product product = (Product) row[0];
    RevisionInfo revision = (RevisionInfo) row[1];
    RevisionType revisionType = (RevisionType) row[2];

    String categoryName = null;
    try {
      if (product.getCategory() != null) {
        categoryName = product.getCategory().getName();
      }
    } catch (LazyInitializationException ex) {
      log.warn("Could not resolve category for product id={}: {}", product.getId(), ex.getMessage());
    }

    return new ProductAuditResponse(
        revision.getRev(),
        Instant.ofEpochMilli(revision.getRevtstmp()),
        revision.getUsername(),
        revisionType,
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getPrice(),
        product.getStock(),
        product.getMinimumStock(),
        product.getActive(),
        categoryName);
  }
}
