package com.inventory.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductMapperTest {

  private ProductMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ProductMapperImpl();
  }

  // ── toEntity ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("toEntity - maps all fields from create request")
  void toEntity_validRequest_mapsFields() {
    var request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    Product result = mapper.toEntity(request);

    assertThat(result.getSku()).isEqualTo("SKU-001");
    assertThat(result.getName()).isEqualTo("Laptop");
    assertThat(result.getDescription()).isEqualTo("desc");
    assertThat(result.getPrice()).isEqualByComparingTo("999.99");
    assertThat(result.getStock()).isEqualTo(10);
    assertThat(result.getId()).isNull();
    assertThat(result.getCreatedAt()).isNull();
    assertThat(result.getCategory()).isNull();
  }

  @Test
  @DisplayName("toEntity - returns null when request is null")
  void toEntity_nullRequest_returnsNull() {
    assertThat(mapper.toEntity(null)).isNull();
  }

  // ── toResponse ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("toResponse - returns null when product is null")
  void toResponse_nullProduct_returnsNull() {
    assertThat(mapper.toResponse(null)).isNull();
  }

  @Test
  @DisplayName("toResponse - maps product without category")
  void toResponse_productWithoutCategory_nullCategoryFields() {
    Product product = buildProduct(1L, "SKU-001", "Laptop", null);

    ProductResponse result = mapper.toResponse(product);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.sku()).isEqualTo("SKU-001");
    assertThat(result.name()).isEqualTo("Laptop");
    assertThat(result.categoryId()).isNull();
    assertThat(result.categoryName()).isNull();
  }

  @Test
  @DisplayName("toResponse - maps product with category")
  void toResponse_productWithCategory_mapsCategoryFields() {
    Category cat = new Category();
    cat.setId(5L);
    cat.setName("Electronics");

    Product product = buildProduct(2L, "SKU-002", "Monitor", cat);

    ProductResponse result = mapper.toResponse(product);

    assertThat(result.categoryId()).isEqualTo(5L);
    assertThat(result.categoryName()).isEqualTo("Electronics");
  }

  @Test
  @DisplayName("toResponse - maps all scalar fields correctly")
  void toResponse_fullProduct_mapsAllFields() {
    Instant now = Instant.now();
    Product product = new Product();
    product.setId(3L);
    product.setSku("SKU-003");
    product.setName("Keyboard");
    product.setDescription("Mechanical keyboard");
    product.setPrice(new BigDecimal("79.99"));
    product.setStock(50);
    product.setMinimumStock(5);
    product.setActive(true);
    product.setCreatedAt(now);
    product.setUpdatedAt(now);

    ProductResponse result = mapper.toResponse(product);

    assertThat(result.description()).isEqualTo("Mechanical keyboard");
    assertThat(result.price()).isEqualByComparingTo("79.99");
    assertThat(result.stock()).isEqualTo(50);
    assertThat(result.minimumStock()).isEqualTo(5);
    assertThat(result.active()).isTrue();
    assertThat(result.createdAt()).isEqualTo(now);
    assertThat(result.updatedAt()).isEqualTo(now);
  }

  // ── updateEntity ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("updateEntity - does nothing when request is null")
  void updateEntity_nullRequest_noChange() {
    Product product = buildProduct(1L, "ORIGINAL", "Original", null);

    mapper.updateEntity(null, product);

    assertThat(product.getSku()).isEqualTo("ORIGINAL");
  }

  @Test
  @DisplayName("updateEntity - overwrites all mutable fields")
  void updateEntity_validRequest_updatesFields() {
    Product product = buildProduct(1L, "SKU-OLD", "Old Name", null);
    var request =
        new ProductUpdateRequest(
            "SKU-NEW", "New Name", "New desc", new BigDecimal("199.99"), 20, 3, false, null);

    mapper.updateEntity(request, product);

    assertThat(product.getSku()).isEqualTo("SKU-NEW");
    assertThat(product.getName()).isEqualTo("New Name");
    assertThat(product.getDescription()).isEqualTo("New desc");
    assertThat(product.getPrice()).isEqualByComparingTo("199.99");
    assertThat(product.getStock()).isEqualTo(20);
    assertThat(product.getMinimumStock()).isEqualTo(3);
    assertThat(product.getActive()).isFalse();
    assertThat(product.getId()).isEqualTo(1L); // ignored field stays
  }

  // ── patchEntity ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("patchEntity - does nothing when request is null")
  void patchEntity_nullRequest_noChange() {
    Product product = buildProduct(1L, "ORIGINAL", "Original", null);

    mapper.patchEntity(null, product);

    assertThat(product.getSku()).isEqualTo("ORIGINAL");
  }

  @Test
  @DisplayName("patchEntity - does not overwrite fields when patch fields are null")
  void patchEntity_allNullFields_preservesExistingValues() {
    Product product = buildProduct(1L, "SKU-KEEP", "Keep Name", null);
    product.setPrice(new BigDecimal("50.00"));
    product.setStock(15);
    product.setMinimumStock(2);
    product.setActive(true);

    var request = new ProductPatchRequest(null, null, null, null, null, null, null, null);

    mapper.patchEntity(request, product);

    assertThat(product.getSku()).isEqualTo("SKU-KEEP");
    assertThat(product.getName()).isEqualTo("Keep Name");
    assertThat(product.getPrice()).isEqualByComparingTo("50.00");
    assertThat(product.getStock()).isEqualTo(15);
    assertThat(product.getMinimumStock()).isEqualTo(2);
    assertThat(product.getActive()).isTrue();
  }

  @Test
  @DisplayName("patchEntity - updates only provided non-null fields")
  void patchEntity_partialFields_updatesOnlyProvidedFields() {
    Product product = buildProduct(1L, "SKU-001", "Old Name", null);
    product.setPrice(new BigDecimal("100.00"));
    product.setStock(10);

    var request =
        new ProductPatchRequest(
            null, "New Name", null, new BigDecimal("149.99"), null, null, null, null);

    mapper.patchEntity(request, product);

    assertThat(product.getSku()).isEqualTo("SKU-001"); // not changed
    assertThat(product.getName()).isEqualTo("New Name"); // changed
    assertThat(product.getPrice()).isEqualByComparingTo("149.99"); // changed
    assertThat(product.getStock()).isEqualTo(10); // not changed
  }

  @Test
  @DisplayName("patchEntity - updates all fields when all non-null")
  void patchEntity_allFieldsProvided_updatesAll() {
    Product product = buildProduct(1L, "OLD-SKU", "Old", null);

    var request =
        new ProductPatchRequest(
            "NEW-SKU", "New Name", "New desc", new BigDecimal("299.99"), 30, 5, false, null);

    mapper.patchEntity(request, product);

    assertThat(product.getSku()).isEqualTo("NEW-SKU");
    assertThat(product.getName()).isEqualTo("New Name");
    assertThat(product.getDescription()).isEqualTo("New desc");
    assertThat(product.getPrice()).isEqualByComparingTo("299.99");
    assertThat(product.getStock()).isEqualTo(30);
    assertThat(product.getMinimumStock()).isEqualTo(5);
    assertThat(product.getActive()).isFalse();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Product buildProduct(Long id, String sku, String name, Category category) {
    Product p = new Product();
    p.setId(id);
    p.setSku(sku);
    p.setName(name);
    p.setCategory(category);
    return p;
  }
}
