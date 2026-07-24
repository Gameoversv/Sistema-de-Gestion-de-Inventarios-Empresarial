package com.inventory.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.repository.ProductRepository;
import com.inventory.report.dto.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock private ProductRepository productRepository;

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

  // ── stockSummary ──────────────────────────────────────────────────────────

  // Verifica que stockSummary calcula correctamente totales, bajo stock y valor para activos.
  @Test
  @DisplayName("stockSummary returns correct totals for active products")
  void stockSummary_activeProducts_returnsCorrectTotals() {
    Product p1 = product("SKU-1", 20, 5, true, cat);
    Product p2 = product("SKU-2", 3, 5, true, cat); // 3 <= 5 → low stock
    when(productRepository.findAll()).thenReturn(List.of(p1, p2));

    StockSummaryResponse result = reportService.stockSummary();

    assertThat(result.totalProducts()).isEqualTo(2);
    assertThat(result.activeProducts()).isEqualTo(2);
    assertThat(result.lowStockProducts()).isEqualTo(1);
    // 10*20 + 10*3 = 230
    assertThat(result.totalInventoryValue()).isEqualByComparingTo("230.00");
    assertThat(result.byCategory()).hasSize(1);
    assertThat(result.byCategory().get(0).categoryName()).isEqualTo("Electrónica");
    assertThat(result.byCategory().get(0).productCount()).isEqualTo(2);
    assertThat(result.byCategory().get(0).totalStock()).isEqualTo(23);
  }

  // Verifica que productos inactivos se excluyen del conteo activo y del valor de inventario.
  @Test
  @DisplayName("stockSummary excludes inactive products from active metrics and value")
  void stockSummary_withInactiveProducts_excludesThemFromActiveMetrics() {
    Product active = product("SKU-A", 10, 2, true, cat);
    Product inactive = product("SKU-I", 50, 5, false, cat);
    when(productRepository.findAll()).thenReturn(List.of(active, inactive));

    StockSummaryResponse result = reportService.stockSummary();

    assertThat(result.totalProducts()).isEqualTo(2);
    assertThat(result.activeProducts()).isEqualTo(1);
    assertThat(result.lowStockProducts()).isZero();
    assertThat(result.totalInventoryValue()).isEqualByComparingTo("100.00");
    assertThat(result.byCategory()).hasSize(1);
    assertThat(result.byCategory().get(0).productCount()).isEqualTo(1);
  }

  // Verifica que productos sin categoría se agrupan bajo el nombre "Sin categoría".
  @Test
  @DisplayName("stockSummary groups products without category under 'Sin categoría'")
  void stockSummary_nullCategory_groupsUnderSinCategoria() {
    Product nocat = product("SKU-NC", 5, 2, true, null);
    when(productRepository.findAll()).thenReturn(List.of(nocat));

    StockSummaryResponse result = reportService.stockSummary();

    assertThat(result.byCategory()).hasSize(1);
    assertThat(result.byCategory().get(0).categoryName()).isEqualTo("Sin categoría");
  }

  // Verifica que stockSummary retorna ceros y lista vacía cuando el repositorio está vacío.
  @Test
  @DisplayName("stockSummary returns zeros when repository is empty")
  void stockSummary_emptyRepository_returnsZeros() {
    when(productRepository.findAll()).thenReturn(List.of());

    StockSummaryResponse result = reportService.stockSummary();

    assertThat(result.totalProducts()).isZero();
    assertThat(result.activeProducts()).isZero();
    assertThat(result.lowStockProducts()).isZero();
    assertThat(result.totalInventoryValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.byCategory()).isEmpty();
  }

  // Verifica que las categorías en byCategory se ordenan alfabéticamente.
  @Test
  @DisplayName("stockSummary sorts byCategory alphabetically")
  void stockSummary_multipleCategories_sortedAlphabetically() {
    Category catZ = Category.builder().name("Zapatos").build();
    Category catA = Category.builder().name("Accesorios").build();
    Product pZ = product("SKU-Z", 10, 2, true, catZ);
    Product pA = product("SKU-A", 5, 2, true, catA);
    when(productRepository.findAll()).thenReturn(List.of(pZ, pA));

    StockSummaryResponse result = reportService.stockSummary();

    assertThat(result.byCategory())
        .extracting(CategoryStockDto::categoryName)
        .containsExactly("Accesorios", "Zapatos");
  }

  // ── lowStockAlert ─────────────────────────────────────────────────────────

  // Verifica que con threshold=0 se retornan todos los productos del repositorio sin filtrar.
  @Test
  @DisplayName("lowStockAlert threshold=0 returns all products from repository")
  void lowStockAlert_thresholdZero_returnsAll() {
    Product p1 = product("SKU-1", 3, 10, true, cat);
    Product p2 = product("SKU-2", 1, 5, true, null);
    when(productRepository.findLowStockProducts()).thenReturn(List.of(p1, p2));

    LowStockReportResponse result = reportService.lowStockAlert(0);

    assertThat(result.threshold()).isZero();
    assertThat(result.count()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
  }

  // Verifica que con threshold positivo solo se incluyen productos con stock <= threshold.
  @Test
  @DisplayName("lowStockAlert threshold>0 filters to products with stock <= threshold")
  void lowStockAlert_thresholdPositive_filtersItems() {
    Product p1 = product("SKU-1", 2, 10, true, cat); // 2 <= 3 → included
    Product p2 = product("SKU-2", 5, 10, true, cat); // 5 > 3 → excluded
    when(productRepository.findLowStockProducts()).thenReturn(List.of(p1, p2));

    LowStockReportResponse result = reportService.lowStockAlert(3);

    assertThat(result.threshold()).isEqualTo(3);
    assertThat(result.count()).isEqualTo(1);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).sku()).isEqualTo("SKU-1");
  }

  // Verifica que el déficit de un ítem es minimumStock menos el stock actual.
  @Test
  @DisplayName("lowStockAlert item deficit equals minimumStock minus currentStock")
  void lowStockAlert_itemDeficit_isMinimumStockMinusCurrentStock() {
    Product p = product("SKU-D", 3, 10, true, cat); // deficit = 10 - 3 = 7
    when(productRepository.findLowStockProducts()).thenReturn(List.of(p));

    LowStockReportResponse result = reportService.lowStockAlert(0);

    LowStockItemDto item = result.items().get(0);
    assertThat(item.currentStock()).isEqualTo(3);
    assertThat(item.minimumStock()).isEqualTo(10);
    assertThat(item.deficit()).isEqualTo(7);
  }

  // Verifica que un ítem sin categoría se mapea con el nombre "Sin categoría".
  @Test
  @DisplayName("lowStockAlert item with null category maps to 'Sin categoría'")
  void lowStockAlert_nullCategoryItem_mapsToSinCategoria() {
    Product p = product("SKU-NC", 1, 5, true, null);
    when(productRepository.findLowStockProducts()).thenReturn(List.of(p));

    LowStockReportResponse result = reportService.lowStockAlert(0);

    assertThat(result.items().get(0).categoryName()).isEqualTo("Sin categoría");
  }

  // Verifica que lowStockAlert retorna lista vacía cuando no hay productos con bajo stock.
  @Test
  @DisplayName("lowStockAlert returns empty list when no low-stock products exist")
  void lowStockAlert_noLowStock_returnsEmpty() {
    when(productRepository.findLowStockProducts()).thenReturn(List.of());

    LowStockReportResponse result = reportService.lowStockAlert(0);

    assertThat(result.count()).isZero();
    assertThat(result.items()).isEmpty();
  }
}
