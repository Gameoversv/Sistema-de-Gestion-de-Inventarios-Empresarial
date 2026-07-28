package com.inventory.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.ProductAuditResponse;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.LazyInitializationException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditQuery;
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
class ProductAuditServiceTest {

  @Mock EntityManager entityManager;

  @InjectMocks ProductAuditService productAuditService;

  // Verifica que sin revisiones el resultado es una lista vacía.
  @Test
  void findProductHistory_noRevisions_returnsEmptyList() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      new Fixture(factory);

      List<ProductAuditResponse> result = productAuditService.findProductHistory(null);

      assertThat(result).isEmpty();
    }
  }

  // Verifica que una revisión con categoría resoluble expone su nombre.
  @Test
  void findProductHistory_resolvableCategory_exposesCategoryName() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      Product product = product(1L, "SKU-001", "Laptop");
      product.setCategory(category(3L, "Electrónica"));
      fixture.rows(row(product, revision(5, "admin"), RevisionType.MOD));

      List<ProductAuditResponse> result = productAuditService.findProductHistory(null);

      assertThat(result).hasSize(1);
      ProductAuditResponse entry = result.get(0);
      assertThat(entry.categoryName()).isEqualTo("Electrónica");
      assertThat(entry.sku()).isEqualTo("SKU-001");
      assertThat(entry.revisionNumber()).isEqualTo(5);
      assertThat(entry.revisedBy()).isEqualTo("admin");
    }
  }

  /**
   * Las categorías sembradas por Flyway entran por SQL directo, sin pasar por Hibernate, así que
   * Envers nunca las registró en {@code categories_aud}. Al reconstruir una revisión del producto,
   * el proxy de la categoría no encuentra su fila y lanza {@link EntityNotFoundException} —no
   * {@link LazyInitializationException}—, que escapaba y devolvía un 500 en {@code GET
   * /api/audit/products} para todo producto del seed.
   */
  @Test
  void findProductHistory_categoryMissingFromAuditTables_returnsRevisionWithoutCategoryName() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      Product product = mock(Product.class);
      when(product.getId()).thenReturn(7L);
      when(product.getSku()).thenReturn("SKU-SEED");
      when(product.getName()).thenReturn("Producto del seed");
      when(product.getPrice()).thenReturn(new BigDecimal("19.99"));
      when(product.getStock()).thenReturn(2);
      when(product.getMinimumStock()).thenReturn(5);
      when(product.getActive()).thenReturn(true);
      when(product.getCategory())
          .thenThrow(new EntityNotFoundException("Unable to find Category with id 1"));
      fixture.rows(row(product, revision(9, "admin"), RevisionType.ADD));

      List<ProductAuditResponse> result = productAuditService.findProductHistory(null);

      assertThat(result).hasSize(1);
      ProductAuditResponse entry = result.get(0);
      assertThat(entry.categoryName()).isNull();
      assertThat(entry.sku()).isEqualTo("SKU-SEED");
      assertThat(entry.productId()).isEqualTo(7L);
      assertThat(entry.stock()).isEqualTo(2);
    }
  }

  // Verifica que la LazyInitializationException que ya se contemplaba se sigue absorbiendo.
  @Test
  void findProductHistory_lazyCategory_returnsRevisionWithoutCategoryName() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      Product product = mock(Product.class);
      when(product.getId()).thenReturn(8L);
      when(product.getSku()).thenReturn("SKU-LAZY");
      when(product.getCategory()).thenThrow(new LazyInitializationException("no session"));
      fixture.rows(row(product, revision(2, "clerk"), RevisionType.MOD));

      List<ProductAuditResponse> result = productAuditService.findProductHistory(null);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).categoryName()).isNull();
    }
  }

  // Verifica que un producto sin categoría asignada no falla y deja el nombre a null.
  @Test
  void findProductHistory_productWithoutCategory_leavesCategoryNameNull() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rows(row(product(4L, "SKU-004", "Sin categoría"), revision(1, "admin")));

      List<ProductAuditResponse> result = productAuditService.findProductHistory(null);

      assertThatCode(() -> productAuditService.findProductHistory(null)).doesNotThrowAnyException();
      assertThat(result.get(0).categoryName()).isNull();
    }
  }

  // ── infraestructura de test ───────────────────────────────────────────────

  /** Prepara el {@link AuditReader} estático y devuelve, por defecto, una lista vacía. */
  private final class Fixture {

    private final AuditQueryCreator creator = mock(AuditQueryCreator.class);

    Fixture(MockedStatic<AuditReaderFactory> factory) {
      AuditReader reader = mock(AuditReader.class);
      factory.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(reader);
      when(reader.createQuery()).thenReturn(creator);
      rows();
    }

    void rows(Object[]... rows) {
      AuditQuery query = mock(AuditQuery.class);
      when(query.getResultList()).thenReturn(List.of(rows));
      when(creator.forRevisionsOfEntity(Product.class, false, true)).thenReturn(query);
    }
  }

  private static Object[] row(Object entity, RevisionInfo revision) {
    return row(entity, revision, RevisionType.MOD);
  }

  private static Object[] row(Object entity, RevisionInfo revision, RevisionType type) {
    return new Object[] {entity, revision, type};
  }

  private static RevisionInfo revision(int rev, String username) {
    RevisionInfo revision = new RevisionInfo();
    revision.setRev(rev);
    revision.setRevtstmp(1_000_000L);
    revision.setUsername(username);
    return revision;
  }

  private static Product product(Long id, String sku, String name) {
    Product product =
        Product.builder()
            .sku(sku)
            .name(name)
            .stock(1)
            .minimumStock(1)
            .price(BigDecimal.ONE)
            .active(true)
            .build();
    product.setId(id);
    return product;
  }

  private static Category category(Long id, String name) {
    Category category = Category.builder().name(name).build();
    category.setId(id);
    return category;
  }
}
