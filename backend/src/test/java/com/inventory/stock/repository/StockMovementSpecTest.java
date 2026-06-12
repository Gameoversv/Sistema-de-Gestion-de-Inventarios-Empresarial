package com.inventory.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMovementSpecTest {

  @SuppressWarnings("unchecked")
  private Root<StockMovement> root;

  @SuppressWarnings("unchecked")
  private CriteriaQuery<?> query;

  private CriteriaBuilder cb;
  private Predicate dummyPredicate;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    root = mock(Root.class);
    query = mock(CriteriaQuery.class);
    cb = mock(CriteriaBuilder.class);
    dummyPredicate = mock(Predicate.class);

    Path<Object> productPath = mock(Path.class);
    Path<Object> productIdPath = mock(Path.class);
    Path<Object> typePath = mock(Path.class);
    Path<Object> createdAtPath = mock(Path.class);

    when(root.get("product")).thenReturn(productPath);
    when(productPath.get("id")).thenReturn(productIdPath);
    when(root.get("type")).thenReturn(typePath);
    when(root.get("createdAt")).thenReturn(createdAtPath);

    when(cb.equal(any(), any())).thenReturn(dummyPredicate);
    when(cb.greaterThanOrEqualTo(any(), (Comparable) any())).thenReturn(dummyPredicate);
    when(cb.lessThanOrEqualTo(any(), (Comparable) any())).thenReturn(dummyPredicate);
    when(cb.and(any(Predicate[].class))).thenReturn(dummyPredicate);
  }

  // ── no filters ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - all null params produces empty predicate list")
  void filtered_allNullParams_noPredicatesAdded() {
    Specification<StockMovement> spec = StockMovementSpec.filtered(null, null, null, null);

    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isNotNull();
    verify(root, never()).get(any(String.class));
    verify(cb, never()).greaterThanOrEqualTo(any(), (Comparable) any());
    verify(cb, never()).lessThanOrEqualTo(any(), (Comparable) any());
  }

  // ── productId filter ──────────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - productId navigates into product.id")
  void filtered_withProductId_navigatesToProductId() {
    Specification<StockMovement> spec = StockMovementSpec.filtered(42L, null, null, null);

    spec.toPredicate(root, query, cb);

    verify(root).get("product");
  }

  @Test
  @DisplayName("filtered - null productId skips product navigation")
  void filtered_nullProductId_skipsProductNavigation() {
    Specification<StockMovement> spec =
        StockMovementSpec.filtered(null, MovementType.IN, null, null);

    spec.toPredicate(root, query, cb);

    verify(root, never()).get("product");
  }

  // ── type filter ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - type navigates to type field")
  void filtered_withType_navigatesToTypeField() {
    Specification<StockMovement> spec =
        StockMovementSpec.filtered(null, MovementType.OUT, null, null);

    spec.toPredicate(root, query, cb);

    verify(root).get("type");
  }

  @Test
  @DisplayName("filtered - null type skips type navigation")
  void filtered_nullType_skipsTypeNavigation() {
    Specification<StockMovement> spec = StockMovementSpec.filtered(1L, null, null, null);

    spec.toPredicate(root, query, cb);

    verify(root, never()).get("type");
  }

  // ── from filter ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - from builds greaterThanOrEqualTo predicate")
  void filtered_withFrom_buildsFromPredicate() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Specification<StockMovement> spec = StockMovementSpec.filtered(null, null, from, null);

    spec.toPredicate(root, query, cb);

    verify(cb).greaterThanOrEqualTo(any(), (Comparable) any());
  }

  @Test
  @DisplayName("filtered - null from skips greaterThanOrEqualTo")
  void filtered_nullFrom_skipsFromPredicate() {
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    Specification<StockMovement> spec = StockMovementSpec.filtered(null, null, null, to);

    spec.toPredicate(root, query, cb);

    verify(cb, never()).greaterThanOrEqualTo(any(), (Comparable) any());
  }

  // ── to filter ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - to builds lessThanOrEqualTo predicate")
  void filtered_withTo_buildsToPredicate() {
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    Specification<StockMovement> spec = StockMovementSpec.filtered(null, null, null, to);

    spec.toPredicate(root, query, cb);

    verify(cb).lessThanOrEqualTo(any(), (Comparable) any());
  }

  @Test
  @DisplayName("filtered - null to skips lessThanOrEqualTo")
  void filtered_nullTo_skipsToPredicate() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Specification<StockMovement> spec = StockMovementSpec.filtered(null, null, from, null);

    spec.toPredicate(root, query, cb);

    verify(cb, never()).lessThanOrEqualTo(any(), (Comparable) any());
  }

  // ── all filters combined ──────────────────────────────────────────────────

  @Test
  @DisplayName("filtered - all params navigates all fields")
  void filtered_allParams_navigatesAllFields() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    Specification<StockMovement> spec = StockMovementSpec.filtered(1L, MovementType.IN, from, to);

    spec.toPredicate(root, query, cb);

    verify(root).get("product");
    verify(root).get("type");
    verify(cb).greaterThanOrEqualTo(any(), (Comparable) any());
    verify(cb).lessThanOrEqualTo(any(), (Comparable) any());
  }
}
