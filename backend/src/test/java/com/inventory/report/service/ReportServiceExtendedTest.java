package com.inventory.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import com.inventory.report.dto.*;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReportServiceExtendedTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private StockMovementRepository stockMovementRepository;

  @InjectMocks private ReportServiceImpl reportService;

  private Category cat;

  @BeforeEach
  void setUp() {
    cat = Category.builder().name("Electrónica").build();
  }

  private Product product(String sku, int stock, int minStock, boolean active, Category category) {
    return Product.builder()
        .sku(sku)
        .name(sku + "-name")
        .price(BigDecimal.TEN)
        .stock(stock)
        .minimumStock(minStock)
        .active(active)
        .category(category)
        .build();
  }

  // ── criticalStock ──────────────────────────────────────────────────────────

  // Verifica que criticalStock retorna solo los productos con stock igual a cero.
  @Test
  @DisplayName("criticalStock returns only products with stock = 0")
  void criticalStock_returnsOnlyZeroStockProducts() {
    Product zero = product("SKU-Z", 0, 5, true, cat);
    when(productRepository.findCriticalStockProducts()).thenReturn(List.of(zero));

    CriticalStockResponse result = reportService.criticalStock();

    assertThat(result.count()).isEqualTo(1);
    assertThat(result.products()).hasSize(1);
    assertThat(result.products().get(0).sku()).isEqualTo("SKU-Z");
    assertThat(result.products().get(0).currentStock()).isZero();
    assertThat(result.products().get(0).deficit()).isEqualTo(5);
  }

  // Verifica que criticalStock retorna respuesta vacía cuando no hay productos con stock cero.
  @Test
  @DisplayName("criticalStock returns empty response when no zero-stock products")
  void criticalStock_noZeroStock_returnsEmpty() {
    when(productRepository.findCriticalStockProducts()).thenReturn(List.of());

    CriticalStockResponse result = reportService.criticalStock();

    assertThat(result.count()).isZero();
    assertThat(result.products()).isEmpty();
  }

  // ── topProducts ───────────────────────────────────────────────────────────
  //
  // Desde D-3 el orden, el filtro de activos y el recorte los hace la base. Estos tests verifican
  // la parte que sigue siendo del servicio —a qué consulta delega según la métrica, qué límite le
  // pasa y cómo mapea el resultado—; que el ORDER BY y el WHERE hagan lo que dicen se comprueba
  // contra Postgres en ProductRepositoryIT, porque un mock no puede ordenar.

  // Verifica que con metric=value se delega en la consulta ordenada por precio×stock.
  @Test
  @DisplayName("topProducts metric=value delegates to the inventory-value query")
  void topProducts_byValue_delegatesToValueQuery() {
    Product p = product("SKU-B", 50, 2, true, cat); // value = 500
    when(productRepository.findTopByInventoryValue(any(Pageable.class))).thenReturn(List.of(p));

    TopProductsResponse result = reportService.topProducts(10, "value");

    assertThat(result.metric()).isEqualTo("value");
    assertThat(result.products()).extracting(TopProductDto::sku).containsExactly("SKU-B");
    assertThat(result.products().get(0).inventoryValue()).isEqualByComparingTo("500.00");
    assertThat(result.products().get(0).categoryName()).isEqualTo("Electrónica");
    verify(productRepository, never()).findTopByStock(any());
  }

  // Verifica que con metric=quantity se delega en la consulta ordenada por unidades.
  @Test
  @DisplayName("topProducts metric=quantity delegates to the stock-units query")
  void topProducts_byQuantity_delegatesToStockQuery() {
    Product p = product("SKU-B", 100, 2, true, cat);
    when(productRepository.findTopByStock(any(Pageable.class))).thenReturn(List.of(p));

    TopProductsResponse result = reportService.topProducts(10, "quantity");

    assertThat(result.metric()).isEqualTo("quantity");
    assertThat(result.products()).extracting(TopProductDto::sku).containsExactly("SKU-B");
    verify(productRepository, never()).findTopByInventoryValue(any());
  }

  // Verifica que el límite viaja a la consulta como tamaño de página, y no se aplica después en
  // memoria: es lo que impide traerse la tabla entera.
  @Test
  @DisplayName("topProducts pushes the limit down to the query as page size")
  void topProducts_limitTravelsToQuery() {
    when(productRepository.findTopByInventoryValue(any(Pageable.class))).thenReturn(List.of());

    TopProductsResponse result = reportService.topProducts(2, "value");

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(productRepository).findTopByInventoryValue(captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(2);
    assertThat(captor.getValue().getPageNumber()).isZero();
    assertThat(result.limit()).isEqualTo(2);
  }

  // Verifica que con limit<=0 se usa el valor por defecto de 10, también en la consulta.
  @Test
  @DisplayName("topProducts uses default limit=10 when limit<=0")
  void topProducts_zeroLimit_usesDefault10() {
    when(productRepository.findTopByInventoryValue(any(Pageable.class))).thenReturn(List.of());

    TopProductsResponse result = reportService.topProducts(0, "value");

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(productRepository).findTopByInventoryValue(captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    assertThat(result.limit()).isEqualTo(10);
  }

  // Verifica que una métrica desconocida cae en el ranking por valor, el mismo que el defecto.
  @Test
  @DisplayName("topProducts falls back to the value ranking for an unknown metric")
  void topProducts_unknownMetric_fallsBackToValue() {
    when(productRepository.findTopByInventoryValue(any(Pageable.class))).thenReturn(List.of());

    TopProductsResponse result = reportService.topProducts(5, "profit");

    assertThat(result.metric()).isEqualTo("profit");
    verify(productRepository).findTopByInventoryValue(any());
    verify(productRepository, never()).findTopByStock(any());
  }

  // ── dashboardMetrics ──────────────────────────────────────────────────────

  // Verifica que dashboardMetrics agrega correctamente todos los contadores del dashboard.
  @Test
  @DisplayName("dashboardMetrics aggregates all counters correctly")
  void dashboardMetrics_aggregatesCountersCorrectly() {
    Product active1 = product("SKU-1", 10, 5, true, cat); // stock > min
    Product active2 = product("SKU-2", 3, 5, true, cat); // low stock (3<=5)
    Product active3 = product("SKU-3", 0, 5, true, cat); // critical (0) AND low stock
    Product inactive = product("SKU-I", 50, 5, false, cat);

    StockMovement lastMovement =
        StockMovement.builder().product(active1).type(MovementType.IN).quantity(10).build();
    lastMovement.setCreatedAt(Instant.parse("2026-05-29T10:00:00Z"));

    when(productRepository.findAll()).thenReturn(List.of(active1, active2, active3, inactive));
    when(categoryRepository.count()).thenReturn(3L);
    when(stockMovementRepository.count()).thenReturn(42L);
    when(stockMovementRepository.findFirstByOrderByCreatedAtDesc())
        .thenReturn(Optional.of(lastMovement));

    DashboardMetricsResponse result = reportService.dashboardMetrics();

    assertThat(result.totalProducts()).isEqualTo(4);
    assertThat(result.activeProducts()).isEqualTo(3);
    assertThat(result.inactiveProducts()).isEqualTo(1);
    assertThat(result.totalCategories()).isEqualTo(3L);
    assertThat(result.totalStockMovements()).isEqualTo(42L);
    // value = 10*10 + 10*3 + 10*0 = 130 (inactive excluded)
    assertThat(result.totalInventoryValue()).isEqualByComparingTo("130.00");
    assertThat(result.lowStockCount()).isEqualTo(2); // SKU-2 and SKU-3
    assertThat(result.criticalStockCount()).isEqualTo(1); // SKU-3
    assertThat(result.lastMovementAt()).isEqualTo(Instant.parse("2026-05-29T10:00:00Z"));
  }

  // Verifica que lastMovementAt es null cuando no existen movimientos de stock registrados.
  @Test
  @DisplayName("dashboardMetrics returns null lastMovementAt when no movements exist")
  void dashboardMetrics_noMovements_lastMovementAtIsNull() {
    when(productRepository.findAll()).thenReturn(List.of());
    when(categoryRepository.count()).thenReturn(0L);
    when(stockMovementRepository.count()).thenReturn(0L);
    when(stockMovementRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

    DashboardMetricsResponse result = reportService.dashboardMetrics();

    assertThat(result.lastMovementAt()).isNull();
    assertThat(result.totalProducts()).isZero();
  }

  // ── recentMovements ───────────────────────────────────────────────────────

  // Verifica que recentMovements retorna los movimientos mapeados correctamente al DTO.
  @Test
  @DisplayName("recentMovements returns movements mapped to DTO")
  void recentMovements_returnsMappedMovements() {
    Product p = product("SKU-X", 50, 5, true, cat);
    StockMovement m =
        StockMovement.builder()
            .product(p)
            .type(MovementType.IN)
            .quantity(20)
            .quantityBefore(30)
            .quantityAfter(50)
            .performedBy("admin")
            .build();
    m.setCreatedAt(Instant.parse("2026-05-29T09:00:00Z"));

    when(stockMovementRepository.findRecent(any(Pageable.class))).thenReturn(List.of(m));

    RecentMovementsResponse result = reportService.recentMovements(10);

    assertThat(result.limit()).isEqualTo(10);
    assertThat(result.count()).isEqualTo(1);
    assertThat(result.movements()).hasSize(1);

    RecentMovementDto dto = result.movements().get(0);
    assertThat(dto.sku()).isEqualTo("SKU-X");
    assertThat(dto.type()).isEqualTo(MovementType.IN);
    assertThat(dto.quantity()).isEqualTo(20);
    assertThat(dto.quantityBefore()).isEqualTo(30);
    assertThat(dto.quantityAfter()).isEqualTo(50);
    assertThat(dto.performedBy()).isEqualTo("admin");
  }

  // Verifica que con limit<=0 se usa el valor por defecto de 20 movimientos recientes.
  @Test
  @DisplayName("recentMovements uses default limit=20 when limit<=0")
  void recentMovements_zeroLimit_usesDefault20() {
    when(stockMovementRepository.findRecent(any(Pageable.class))).thenReturn(List.of());

    RecentMovementsResponse result = reportService.recentMovements(0);

    assertThat(result.limit()).isEqualTo(20);
  }

  // Verifica que recentMovements retorna lista vacía cuando no existen movimientos.
  @Test
  @DisplayName("recentMovements returns empty list when no movements exist")
  void recentMovements_noMovements_returnsEmpty() {
    when(stockMovementRepository.findRecent(any(Pageable.class))).thenReturn(List.of());

    RecentMovementsResponse result = reportService.recentMovements(5);

    assertThat(result.count()).isZero();
    assertThat(result.movements()).isEmpty();
  }
}
