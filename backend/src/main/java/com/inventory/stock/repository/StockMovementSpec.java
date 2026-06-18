package com.inventory.stock.repository;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Fábrica de especificaciones JPA para filtrar movimientos de stock dinámicamente. Genera
 * predicados combinados por producto, tipo de movimiento (IN/OUT/ADJUSTMENT) y rango de fechas
 * (from/to), ignorando los parámetros nulos.
 */
public final class StockMovementSpec {

  private StockMovementSpec() {}

  public static Specification<StockMovement> filtered(
      Long productId, MovementType type, Instant from, Instant to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (productId != null) {
        predicates.add(cb.equal(root.get("product").get("id"), productId));
      }
      if (type != null) {
        predicates.add(cb.equal(root.get("type"), type));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
