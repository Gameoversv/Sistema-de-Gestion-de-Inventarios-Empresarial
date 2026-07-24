package com.inventory.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.inventory.audit.domain.RevisionInfo;
import com.inventory.audit.dto.UnifiedAuditEntry;
import com.inventory.audit.dto.UnifiedAuditEntry.EntityType;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.security.domain.AppUser;
import com.inventory.security.domain.AppUser.Role;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
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
class UnifiedAuditServiceTest {

  @Mock EntityManager entityManager;

  @InjectMocks UnifiedAuditService unifiedAuditService;

  // ── findAll: casos vacíos y de error ──────────────────────────────────────

  // Verifica que sin revisiones en ninguna entidad auditada el resultado es una lista vacía.
  @Test
  void findAll_noRevisions_returnsEmptyList() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      // El constructor arma el mock estático; no hace falta quedarse la referencia.
      new Fixture(factory);

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).isEmpty();
    }
  }

  // Verifica que si la consulta Envers de una entidad falla, esa entidad se omite sin propagar
  // la excepción y el resto del historial se devuelve igualmente.
  @Test
  void findAll_queryFailsForOneEntity_skipsItAndKeepsTheRest() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.failFor(Product.class, new RuntimeException("envers table missing"));
      fixture.rowsFor(Category.class, row(category(7L, "Periféricos"), revision(3, "admin")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).entityType()).isEqualTo(EntityType.CATEGORY);
    }
  }

  // ── findAll: Product ──────────────────────────────────────────────────────

  // Verifica que una revisión de producto se mapea con su resumen de sku, nombre, stock y precio.
  @Test
  void findAll_productRevision_mapsSkuNameStockAndPrice() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      Product product = product(42L, "SKU-001", "Laptop", 15, new BigDecimal("999.99"));
      fixture.rowsFor(Product.class, row(product, revision(5, "admin"), RevisionType.MOD));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      UnifiedAuditEntry entry = result.get(0);
      assertThat(entry.entityType()).isEqualTo(EntityType.PRODUCT);
      assertThat(entry.entityId()).isEqualTo(42L);
      assertThat(entry.revisionNumber()).isEqualTo(5);
      assertThat(entry.revisedBy()).isEqualTo("admin");
      assertThat(entry.revisionType()).isEqualTo(RevisionType.MOD);
      assertThat(entry.summary()).isEqualTo("SKU-001 | Laptop | Stock: 15 | $999.99");
    }
  }

  // Verifica que un producto sin id persistido resuelve su entityId a 0 en lugar de fallar.
  @Test
  void findAll_productWithoutId_resolvesEntityIdToZero() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      Product product = product(null, "SKU-002", "Teclado", 3, new BigDecimal("25.00"));
      fixture.rowsFor(Product.class, row(product, revision(1, "admin")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).entityId()).isZero();
    }
  }

  // ── findAll: Category ─────────────────────────────────────────────────────

  // Verifica que una revisión de categoría usa el nombre de la categoría como resumen.
  @Test
  void findAll_categoryRevision_usesNameAsSummary() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rowsFor(
          Category.class, row(category(8L, "Portátiles"), revision(2, "admin"), RevisionType.ADD));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).entityType()).isEqualTo(EntityType.CATEGORY);
      assertThat(result.get(0).entityId()).isEqualTo(8L);
      assertThat(result.get(0).summary()).isEqualTo("Portátiles");
    }
  }

  // Verifica que una categoría sin id persistido resuelve su entityId a 0.
  @Test
  void findAll_categoryWithoutId_resolvesEntityIdToZero() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rowsFor(Category.class, row(category(null, "Sin id"), revision(1, "admin")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).entityId()).isZero();
    }
  }

  // ── findAll: StockMovement ────────────────────────────────────────────────

  // Verifica que un movimiento con producto asociado incluye sku y nombre en el resumen.
  @Test
  void findAll_movementWithProduct_includesProductInSummary() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      StockMovement movement = movement(3L, MovementType.IN, 20);
      movement.setProduct(product(1L, "SKU-009", "Monitor", 5, BigDecimal.TEN));
      fixture.rowsFor(StockMovement.class, row(movement, revision(4, "clerk")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).entityType()).isEqualTo(EntityType.STOCK_MOVEMENT);
      assertThat(result.get(0).summary()).isEqualTo("IN 20 unid. | SKU-009 - Monitor");
    }
  }

  // Verifica que un movimiento sin producto asociado produce un resumen sin la parte de producto.
  @Test
  void findAll_movementWithoutProduct_omitsProductFromSummary() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      StockMovement movement = movement(4L, MovementType.OUT, 7);
      fixture.rowsFor(StockMovement.class, row(movement, revision(6, "clerk")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).summary()).isEqualTo("OUT 7 unid.");
    }
  }

  // Verifica que una LazyInitializationException al acceder al producto se absorbe y el resumen
  // se genera igualmente sin la información del producto.
  @Test
  void findAll_movementWithLazyProduct_absorbsExceptionAndOmitsProduct() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      StockMovement movement = mock(StockMovement.class);
      when(movement.getId()).thenReturn(5L);
      when(movement.getType()).thenReturn(MovementType.ADJUSTMENT);
      when(movement.getQuantity()).thenReturn(2);
      when(movement.getProduct()).thenThrow(new LazyInitializationException("no session"));
      fixture.rowsFor(StockMovement.class, row(movement, revision(9, "system")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).summary()).isEqualTo("ADJUSTMENT 2 unid.");
      assertThat(result.get(0).entityId()).isEqualTo(5L);
    }
  }

  // Verifica que un movimiento sin id persistido resuelve su entityId a 0.
  @Test
  void findAll_movementWithoutId_resolvesEntityIdToZero() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rowsFor(
          StockMovement.class, row(movement(null, MovementType.IN, 1), revision(1, "clerk")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).entityId()).isZero();
    }
  }

  // ── findAll: AppUser ──────────────────────────────────────────────────────

  // Verifica que una revisión de usuario con nombre visible lo incluye junto a email y rol.
  @Test
  void findAll_userWithDisplayName_includesNameEmailAndRole() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      AppUser user = user(11L, "Ana Gómez", "ana@pucmm.edu.do", Role.ADMIN);
      fixture.rowsFor(AppUser.class, row(user, revision(12, "admin"), RevisionType.ADD));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).entityType()).isEqualTo(EntityType.USER);
      assertThat(result.get(0).entityId()).isEqualTo(11L);
      assertThat(result.get(0).summary()).isEqualTo("Ana Gómez (ana@pucmm.edu.do) | Rol: ADMIN");
    }
  }

  // Verifica que un usuario sin nombre visible produce un resumen con la parte de nombre vacía,
  // en lugar de imprimir "null".
  @Test
  void findAll_userWithoutDisplayName_leavesNameBlank() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      AppUser user = user(12L, null, "sin.nombre@pucmm.edu.do", Role.VIEWER);
      fixture.rowsFor(AppUser.class, row(user, revision(13, "admin")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).summary()).isEqualTo(" (sin.nombre@pucmm.edu.do) | Rol: VIEWER");
      assertThat(result.get(0).summary()).doesNotContain("null");
    }
  }

  // Verifica que un usuario sin id persistido resuelve su entityId a 0.
  @Test
  void findAll_userWithoutId_resolvesEntityIdToZero() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rowsFor(
          AppUser.class,
          row(user(null, "Sin Id", "x@pucmm.edu.do", Role.MANAGER), revision(1, "a")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).entityId()).isZero();
    }
  }

  // ── findAll: consolidación ────────────────────────────────────────────────

  // Verifica que las revisiones de las cuatro entidades se consolidan en una sola lista ordenada
  // por número de revisión descendente.
  @Test
  void findAll_revisionsFromAllEntities_areSortedByRevisionNumberDescending() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      fixture.rowsFor(
          Product.class, row(product(1L, "SKU-1", "P", 1, BigDecimal.ONE), revision(10, "admin")));
      fixture.rowsFor(Category.class, row(category(2L, "C"), revision(30, "admin")));
      fixture.rowsFor(
          StockMovement.class, row(movement(3L, MovementType.IN, 1), revision(20, "clerk")));
      fixture.rowsFor(
          AppUser.class, row(user(4L, "U", "u@pucmm.edu.do", Role.VIEWER), revision(40, "admin")));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result).hasSize(4);
      assertThat(result)
          .extracting(UnifiedAuditEntry::revisionNumber)
          .containsExactly(40, 30, 20, 10);
      assertThat(result)
          .extracting(UnifiedAuditEntry::entityType)
          .containsExactly(
              EntityType.USER, EntityType.CATEGORY, EntityType.STOCK_MOVEMENT, EntityType.PRODUCT);
    }
  }

  // Verifica que el timestamp de la revisión se traduce de epoch millis a Instant.
  @Test
  void findAll_revisionTimestamp_isConvertedFromEpochMillis() {
    try (MockedStatic<AuditReaderFactory> factory = mockStatic(AuditReaderFactory.class)) {
      Fixture fixture = new Fixture(factory);
      RevisionInfo revision = revision(1, "admin");
      revision.setRevtstmp(1_700_000_000_000L);
      fixture.rowsFor(Category.class, row(category(1L, "Cualquiera"), revision));

      List<UnifiedAuditEntry> result = unifiedAuditService.findAll();

      assertThat(result.get(0).revisionTimestamp())
          .isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }
  }

  // ── infraestructura de test ───────────────────────────────────────────────

  /**
   * Prepara el {@link AuditReader} estático y devuelve, por defecto, listas vacías para las cuatro
   * entidades auditadas. Cada test declara solo las filas que le interesan.
   */
  private final class Fixture {

    private final AuditQueryCreator creator = mock(AuditQueryCreator.class);

    Fixture(MockedStatic<AuditReaderFactory> factory) {
      AuditReader reader = mock(AuditReader.class);
      factory.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(reader);
      when(reader.createQuery()).thenReturn(creator);

      for (Class<?> entity :
          List.of(Product.class, Category.class, StockMovement.class, AppUser.class)) {
        rowsFor(entity);
      }
    }

    void rowsFor(Class<?> entityClass, Object[]... rows) {
      AuditQuery query = mock(AuditQuery.class);
      when(query.getResultList()).thenReturn(List.of(rows));
      when(creator.forRevisionsOfEntity(entityClass, false, true)).thenReturn(query);
    }

    void failFor(Class<?> entityClass, RuntimeException failure) {
      when(creator.forRevisionsOfEntity(entityClass, false, true)).thenThrow(failure);
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

  private static Product product(Long id, String sku, String name, int stock, BigDecimal price) {
    Product product =
        Product.builder().sku(sku).name(name).stock(stock).price(price).active(true).build();
    product.setId(id);
    return product;
  }

  private static Category category(Long id, String name) {
    Category category = Category.builder().name(name).build();
    category.setId(id);
    return category;
  }

  private static StockMovement movement(Long id, MovementType type, int quantity) {
    StockMovement movement = new StockMovement();
    movement.setId(id);
    movement.setType(type);
    movement.setQuantity(quantity);
    return movement;
  }

  private static AppUser user(Long id, String displayName, String email, Role role) {
    AppUser user = AppUser.builder().displayName(displayName).email(email).role(role).build();
    user.setId(id);
    return user;
  }
}
