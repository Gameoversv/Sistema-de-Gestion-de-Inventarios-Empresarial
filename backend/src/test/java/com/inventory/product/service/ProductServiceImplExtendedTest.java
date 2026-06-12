package com.inventory.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.product.mapper.ProductMapperImpl;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplExtendedTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;

  private ProductServiceImpl productService;
  private Product existingProduct;

  @BeforeEach
  void setUp() {
    ProductMapper mapper = new ProductMapperImpl();
    productService = new ProductServiceImpl(productRepository, categoryRepository, mapper);

    existingProduct = new Product();
    existingProduct.setId(1L);
    existingProduct.setSku("SKU-001");
    existingProduct.setName("Laptop");
    existingProduct.setDescription("A laptop");
    existingProduct.setPrice(new BigDecimal("999.99"));
    existingProduct.setStock(10);
    existingProduct.setMinimumStock(2);
    existingProduct.setActive(true);
    existingProduct.setCreatedAt(Instant.now());
    existingProduct.setUpdatedAt(Instant.now());
  }

  // ── create – branch coverage ───────────────────────────────────────────────

  @Test
  @DisplayName("create - null minimumStock defaults to 0")
  void create_nullMinimumStock_defaultsToZero() {
    var request =
        new ProductCreateRequest(
            "SKU-NEW", "New", "desc", new BigDecimal("10.00"), 5, null, null, null);

    Product saved = new Product();
    saved.setId(2L);
    saved.setSku("SKU-NEW");
    saved.setName("New");
    saved.setPrice(new BigDecimal("10.00"));
    saved.setStock(5);
    saved.setMinimumStock(0);
    saved.setActive(true);

    when(productRepository.existsBySku("SKU-NEW")).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenReturn(saved);

    ProductResponse result = productService.create(request);

    assertThat(result.minimumStock()).isEqualTo(0);
  }

  @Test
  @DisplayName("create - null active defaults to true")
  void create_nullActive_defaultsToTrue() {
    var request =
        new ProductCreateRequest(
            "SKU-ACT", "Active", null, new BigDecimal("5.00"), 1, 0, null, null);

    Product saved = new Product();
    saved.setId(3L);
    saved.setSku("SKU-ACT");
    saved.setName("Active");
    saved.setStock(1);
    saved.setMinimumStock(0);
    saved.setActive(true);

    when(productRepository.existsBySku("SKU-ACT")).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenReturn(saved);

    ProductResponse result = productService.create(request);

    assertThat(result.active()).isTrue();
  }

  @Test
  @DisplayName("create - sets category when categoryId is provided")
  void create_withCategoryId_setsCategory() {
    Category cat = new Category();
    cat.setId(7L);
    cat.setName("Electronics");

    var request =
        new ProductCreateRequest("SKU-CAT", "Item", null, new BigDecimal("20.00"), 1, 0, true, 7L);

    Product saved = new Product();
    saved.setId(4L);
    saved.setSku("SKU-CAT");
    saved.setName("Item");
    saved.setCategory(cat);
    saved.setStock(1);
    saved.setMinimumStock(0);
    saved.setActive(true);

    when(productRepository.existsBySku("SKU-CAT")).thenReturn(false);
    when(categoryRepository.findById(7L)).thenReturn(Optional.of(cat));
    when(productRepository.save(any(Product.class))).thenReturn(saved);

    ProductResponse result = productService.create(request);

    assertThat(result.categoryId()).isEqualTo(7L);
    assertThat(result.categoryName()).isEqualTo("Electronics");
  }

  @Test
  @DisplayName("create - throws ResourceNotFoundException when categoryId not found")
  void create_unknownCategoryId_throwsNotFound() {
    var request =
        new ProductCreateRequest("SKU-XX", "X", null, new BigDecimal("1.00"), 1, 0, true, 99L);

    when(productRepository.existsBySku("SKU-XX")).thenReturn(false);
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── findAll – branch coverage ──────────────────────────────────────────────

  @Test
  @DisplayName("findAll - no filters returns all products")
  void findAll_noFilters_returnsAll() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(existingProduct)));

    Page<ProductResponse> result = productService.findAll(null, null, null, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("findAll - blank search string is ignored")
  void findAll_blankSearch_treatedAsNoFilter() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(existingProduct)));

    Page<ProductResponse> result = productService.findAll("   ", null, null, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("findAll - with search filter delegates to specification")
  void findAll_withSearch_appliesSpec() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(existingProduct)));

    Page<ProductResponse> result =
        productService.findAll("laptop", null, null, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("findAll - with categoryId filter delegates to specification")
  void findAll_withCategoryId_appliesSpec() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(existingProduct)));

    Page<ProductResponse> result = productService.findAll(null, 5L, null, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("findAll - with active filter delegates to specification")
  void findAll_withActive_appliesSpec() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(existingProduct)));

    Page<ProductResponse> result = productService.findAll(null, null, true, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("findAll - with all three filters applies all")
  void findAll_allFilters_appliesAllSpecs() {
    when(productRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of()));

    Page<ProductResponse> result =
        productService.findAll("laptop", 3L, false, PageRequest.of(0, 10));

    assertThat(result.getContent()).isEmpty();
  }

  // ── update – branch coverage ───────────────────────────────────────────────

  @Test
  @DisplayName("update - sets category to null when categoryId is null")
  void update_nullCategoryId_setsNullCategory() {
    var request =
        new ProductUpdateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    ProductResponse result = productService.update(1L, request);

    assertThat(result).isNotNull();
    assertThat(existingProduct.getCategory()).isNull();
  }

  @Test
  @DisplayName("update - resolves category when categoryId is provided")
  void update_withCategoryId_resolvesCategory() {
    Category cat = new Category();
    cat.setId(3L);
    cat.setName("Peripherals");

    var request =
        new ProductUpdateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, 3L);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(categoryRepository.findById(3L)).thenReturn(Optional.of(cat));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.update(1L, request);

    assertThat(existingProduct.getCategory()).isEqualTo(cat);
  }

  @Test
  @DisplayName("update - does not check SKU conflict when SKU unchanged")
  void update_sameSku_noConflictCheck() {
    var request =
        new ProductUpdateRequest(
            "SKU-001", "New Name", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.update(1L, request);

    verify(productRepository, never()).existsBySku(any());
  }

  @Test
  @DisplayName("update - throws ConflictException when new SKU already taken")
  void update_newSkuConflict_throwsConflict() {
    var request =
        new ProductUpdateRequest(
            "SKU-TAKEN", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.existsBySku("SKU-TAKEN")).thenReturn(true);

    assertThatThrownBy(() -> productService.update(1L, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SKU-TAKEN");

    verify(productRepository, never()).save(any());
  }

  // ── patch – branch coverage ────────────────────────────────────────────────

  @Test
  @DisplayName("patch - throws ResourceNotFoundException when product not found")
  void patch_missingProduct_throwsNotFound() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                productService.patch(
                    99L, new ProductPatchRequest(null, null, null, null, null, null, null, null)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  @DisplayName("patch - null sku in request skips conflict check")
  void patch_nullSku_skipsConflictCheck() {
    var request = new ProductPatchRequest(null, "New Name", null, null, null, null, null, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    ProductResponse result = productService.patch(1L, request);

    assertThat(result).isNotNull();
    verify(productRepository, never()).existsBySku(any());
  }

  @Test
  @DisplayName("patch - same sku as existing skips conflict check")
  void patch_sameSku_skipsConflictCheck() {
    var request =
        new ProductPatchRequest("SKU-001", "New Name", null, null, null, null, null, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.patch(1L, request);

    verify(productRepository, never()).existsBySku(any());
  }

  @Test
  @DisplayName("patch - new sku that is not taken succeeds")
  void patch_newSkuNotTaken_succeeds() {
    var request = new ProductPatchRequest("SKU-NEW", "Laptop", null, null, null, null, null, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.existsBySku("SKU-NEW")).thenReturn(false);
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    ProductResponse result = productService.patch(1L, request);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("patch - throws ConflictException when new sku is already taken")
  void patch_newSkuConflict_throwsConflict() {
    var request =
        new ProductPatchRequest("SKU-TAKEN", "Laptop", null, null, null, null, null, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.existsBySku("SKU-TAKEN")).thenReturn(true);

    assertThatThrownBy(() -> productService.patch(1L, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SKU-TAKEN");

    verify(productRepository, never()).save(any());
  }

  @Test
  @DisplayName("patch - resolves category when categoryId is non-null")
  void patch_withCategoryId_resolvesCategory() {
    Category cat = new Category();
    cat.setId(8L);
    cat.setName("Gaming");

    var request = new ProductPatchRequest(null, null, null, null, null, null, null, 8L);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(categoryRepository.findById(8L)).thenReturn(Optional.of(cat));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.patch(1L, request);

    assertThat(existingProduct.getCategory()).isEqualTo(cat);
  }

  @Test
  @DisplayName("patch - does not change category when categoryId is null")
  void patch_nullCategoryId_keepsExistingCategory() {
    Category existing = new Category();
    existing.setId(2L);
    existingProduct.setCategory(existing);

    var request = new ProductPatchRequest(null, "New Name", null, null, null, null, null, null);

    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.patch(1L, request);

    assertThat(existingProduct.getCategory()).isEqualTo(existing);
    verify(categoryRepository, never()).findById(any());
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("delete - soft-deletes by setting active=false")
  void delete_existingProduct_setsActiveFalse() {
    when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
    when(productRepository.save(existingProduct)).thenReturn(existingProduct);

    productService.delete(1L);

    assertThat(existingProduct.getActive()).isFalse();
    verify(productRepository).save(existingProduct);
  }

  @Test
  @DisplayName("delete - throws ResourceNotFoundException when not found")
  void delete_missingProduct_throwsNotFound() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.delete(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");

    verify(productRepository, never()).save(any());
  }

  // ── findBySku ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findBySku - returns response when sku exists")
  void findBySku_existingSku_returnsResponse() {
    when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(existingProduct));

    ProductResponse result = productService.findBySku("SKU-001");

    assertThat(result.sku()).isEqualTo("SKU-001");
  }

  @Test
  @DisplayName("findBySku - throws ResourceNotFoundException when sku not found")
  void findBySku_missingSku_throwsNotFound() {
    when(productRepository.findBySku("MISSING")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.findBySku("MISSING"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("MISSING");
  }
}
