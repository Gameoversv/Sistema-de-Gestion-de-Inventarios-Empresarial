package com.inventory.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.AuditRevisionResponse;
import com.inventory.product.domain.Product;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.LazyInitializationException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditAssociationQuery;
import org.hibernate.envers.query.AuditQueryCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockAuditServiceTest {

  @Mock EntityManager entityManager;

  @InjectMocks StockAuditService stockAuditService;

  @SuppressWarnings({"rawtypes", "unchecked"})
  private AuditAssociationQuery setupMockedReader(
      MockedStatic<AuditReaderFactory> factory, List<Object[]> results) {
    AuditReader reader = mock(AuditReader.class);
    AuditQueryCreator creator = mock(AuditQueryCreator.class);
    AuditAssociationQuery query = mock(AuditAssociationQuery.class);

    factory.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(reader);
    when(reader.createQuery()).thenReturn(creator);
    when(creator.forRevisionsOfEntity(StockMovement.class, false, true)).thenReturn(query);
    when(query.add(any())).thenReturn(query);
    when(query.addOrder(any())).thenReturn(query);
    when(query.getResultList()).thenReturn(results);

    return query;
  }

  // ── findMovementHistory ────────────────────────────────────────────────────

  @Test
  void findMovementHistory_noFilters_returnsEmptyList() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      setupMockedReader(factory, List.of());

      List<AuditRevisionResponse> result =
          stockAuditService.findMovementHistory(null, null, null, null);

      assertThat(result).isEmpty();
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void findMovementHistory_withProductId_addsProductFilter() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      stockAuditService.findMovementHistory(1L, null, null, null);

      verify(query, atLeastOnce()).add(any());
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void findMovementHistory_withUsername_addsUsernameFilter() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      stockAuditService.findMovementHistory(null, "admin", null, null);

      verify(query, atLeastOnce()).add(any());
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void findMovementHistory_withBlankUsername_skipsUsernameFilter() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      stockAuditService.findMovementHistory(null, "   ", null, null);

      verify(query, never()).add(any());
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void findMovementHistory_withFromTimestamp_addsFromFilter() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      stockAuditService.findMovementHistory(
          null, null, Instant.parse("2026-01-01T00:00:00Z"), null);

      verify(query, atLeastOnce()).add(any());
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void findMovementHistory_withToTimestamp_addsToFilter() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      stockAuditService.findMovementHistory(
          null, null, null, Instant.parse("2026-12-31T23:59:59Z"));

      verify(query, atLeastOnce()).add(any());
    }
  }

  @Test
  void findMovementHistory_withMatchingResults_mapsToResponse() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      StockMovement movement = buildMovement("SKU-001", "Laptop", MovementType.IN);
      RevisionInfo revision = buildRevision(1, 1_000_000L, "admin");
      Object[] row = {movement, revision, RevisionType.ADD};

      setupMockedReader(factory, Collections.singletonList(row));

      List<AuditRevisionResponse> result =
          stockAuditService.findMovementHistory(1L, "admin", null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).sku()).isEqualTo("SKU-001");
      assertThat(result.get(0).productName()).isEqualTo("Laptop");
      assertThat(result.get(0).revisedBy()).isEqualTo("admin");
      assertThat(result.get(0).revisionType()).isEqualTo(RevisionType.ADD);
      assertThat(result.get(0).movementType()).isEqualTo(MovementType.IN);
      assertThat(result.get(0).quantity()).isEqualTo(10);
      assertThat(result.get(0).quantityBefore()).isEqualTo(0);
      assertThat(result.get(0).quantityAfter()).isEqualTo(10);
    }
  }

  @Test
  void findMovementHistory_withNullProduct_returnsNullSkuAndProductId() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      StockMovement movement = new StockMovement();
      movement.setType(MovementType.OUT);
      movement.setQuantity(5);
      movement.setQuantityBefore(10);
      movement.setQuantityAfter(5);
      movement.setPerformedBy("operator");
      movement.setReason("sale");

      RevisionInfo revision = buildRevision(2, 2_000_000L, "operator");
      Object[] row = {movement, revision, RevisionType.MOD};

      setupMockedReader(factory, Collections.singletonList(row));

      List<AuditRevisionResponse> result =
          stockAuditService.findMovementHistory(null, null, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).sku()).isNull();
      assertThat(result.get(0).productId()).isNull();
      assertThat(result.get(0).productName()).isNull();
    }
  }

  @Test
  void findMovementHistory_withLazyInitializationException_handlesGracefully() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      StockMovement movement = mock(StockMovement.class);
      when(movement.getProduct()).thenThrow(new LazyInitializationException("lazy load failed"));
      when(movement.getType()).thenReturn(MovementType.ADJUSTMENT);
      when(movement.getQuantity()).thenReturn(3);
      when(movement.getQuantityBefore()).thenReturn(10);
      when(movement.getQuantityAfter()).thenReturn(13);
      when(movement.getPerformedBy()).thenReturn("system");
      when(movement.getReason()).thenReturn("inventory adjustment");

      RevisionInfo revision = buildRevision(3, 3_000_000L, "system");
      Object[] row = {movement, revision, RevisionType.MOD};

      setupMockedReader(factory, Collections.singletonList(row));

      List<AuditRevisionResponse> result =
          stockAuditService.findMovementHistory(null, null, null, null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).sku()).isNull();
      assertThat(result.get(0).productName()).isNull();
      assertThat(result.get(0).productId()).isNull();
      assertThat(result.get(0).movementType()).isEqualTo(MovementType.ADJUSTMENT);
    }
  }

  @Test
  void findMovementHistory_multipleResults_mapsAll() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      StockMovement mov1 = buildMovement("SKU-A", "Product A", MovementType.IN);
      StockMovement mov2 = buildMovement("SKU-B", "Product B", MovementType.OUT);
      RevisionInfo rev1 = buildRevision(10, 1_000_000L, "user1");
      RevisionInfo rev2 = buildRevision(11, 2_000_000L, "user2");

      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] {mov1, rev1, RevisionType.ADD});
      rows.add(new Object[] {mov2, rev2, RevisionType.DEL});
      setupMockedReader(factory, rows);

      List<AuditRevisionResponse> result =
          stockAuditService.findMovementHistory(null, null, null, null);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).sku()).isEqualTo("SKU-A");
      assertThat(result.get(1).sku()).isEqualTo("SKU-B");
      assertThat(result.get(0).revisionType()).isEqualTo(RevisionType.ADD);
      assertThat(result.get(1).revisionType()).isEqualTo(RevisionType.DEL);
    }
  }

  @Test
  void findMovementHistory_withAllFilters_appliesAllCriteria() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      @SuppressWarnings("rawtypes")
      AuditAssociationQuery query = setupMockedReader(factory, List.of());

      Instant from = Instant.parse("2026-01-01T00:00:00Z");
      Instant to = Instant.parse("2026-12-31T23:59:59Z");

      stockAuditService.findMovementHistory(5L, "admin", from, to);

      verify(query, atLeastOnce()).add(any());
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private StockMovement buildMovement(String sku, String name, MovementType type) {
    Product product = new Product();
    product.setSku(sku);
    product.setName(name);

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setType(type);
    movement.setQuantity(10);
    movement.setQuantityBefore(0);
    movement.setQuantityAfter(10);
    movement.setPerformedBy("tester");
    movement.setReason("test");
    return movement;
  }

  private RevisionInfo buildRevision(int rev, long revtstmp, String username) {
    RevisionInfo revision = new RevisionInfo();
    revision.setRev(rev);
    revision.setRevtstmp(revtstmp);
    revision.setUsername(username);
    return revision;
  }
}
