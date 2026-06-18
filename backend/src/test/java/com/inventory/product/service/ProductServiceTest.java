package com.inventory.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.*;
import com.inventory.product.mapper.ProductMapper;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductMapper productMapper;

  @InjectMocks private ProductServiceImpl productService;

  private Product product;
  private ProductResponse productResponse;
  private Category category;

  @BeforeEach
  void setUp() {
    category = new Category();
    category.setName("Electronics");
    category.setDescription("Electronic products");

    product = new Product();
    product.setSku("SKU-001");
    product.setName("Laptop");
    product.setDescription("A laptop");
    product.setPrice(new BigDecimal("999.99"));
    product.setStock(10);
    product.setMinimumStock(2);
    product.setActive(true);
    product.setCategory(category);

    productResponse =
        new ProductResponse(
            1L,
            "SKU-001",
            "Laptop",
            "A laptop",
            new BigDecimal("999.99"),
            10,
            2,
            true,
            null,
            "Electronics",
            Instant.now(),
            Instant.now());
  }

  // ── create ────────────────────────────────────────────────────────────────

  // Verifica que se crea y retorna el producto correctamente cuando el SKU es único.
  @Test
  @DisplayName("create - producto creado correctamente cuando el SKU es único")
  void create_uniqueSku_returnsResponse() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);
    when(productRepository.existsBySku("SKU-001")).thenReturn(false);
    when(productMapper.toEntity(request)).thenReturn(product);
    when(productRepository.save(product)).thenReturn(product);
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    ProductResponse result = productService.create(request);

    assertThat(result.sku()).isEqualTo("SKU-001");
    verify(productRepository).save(product);
  }

  // Verifica que un SKU duplicado lanza ConflictException sin persistir el producto.
  @Test
  @DisplayName("create - lanza ConflictException cuando el SKU ya existe")
  void create_duplicateSku_throwsConflict() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);
    when(productRepository.existsBySku("SKU-001")).thenReturn(true);

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SKU-001");
    verify(productRepository, never()).save(any());
  }

  // Verifica que se resuelve y asigna la categoría cuando se especifica categoryId.
  @Test
  @DisplayName("create - resuelve categoría cuando categoryId no es null")
  void create_withCategory_resolvesCategory() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-002", "Monitor", "desc", new BigDecimal("300.00"), 5, 1, true, 10L);
    when(productRepository.existsBySku("SKU-002")).thenReturn(false);
    when(productMapper.toEntity(request)).thenReturn(product);
    when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
    when(productRepository.save(product)).thenReturn(product);
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    productService.create(request);

    verify(categoryRepository).findById(10L);
    assertThat(product.getCategory()).isEqualTo(category);
  }

  // Verifica que una categoría inexistente lanza ResourceNotFoundException al crear producto.
  @Test
  @DisplayName("create - lanza ResourceNotFoundException cuando la categoría no existe")
  void create_categoryNotFound_throwsNotFound() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "SKU-003", "Tablet", "desc", new BigDecimal("500.00"), 3, 1, true, 99L);
    when(productRepository.existsBySku("SKU-003")).thenReturn(false);
    when(productMapper.toEntity(request)).thenReturn(product);
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── findById ──────────────────────────────────────────────────────────────

  // Verifica que findById retorna el producto cuando el ID existe en el repositorio.
  @Test
  @DisplayName("findById - retorna producto cuando existe")
  void findById_existingId_returnsResponse() {
    when(productRepository.findById(1L)).thenReturn(Optional.of(product));
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    ProductResponse result = productService.findById(1L);

    assertThat(result.id()).isEqualTo(1L);
  }

  // Verifica que findById lanza ResourceNotFoundException cuando el ID no existe.
  @Test
  @DisplayName("findById - lanza ResourceNotFoundException cuando no existe")
  void findById_missingId_throwsNotFound() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.findById(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── findBySku ─────────────────────────────────────────────────────────────

  // Verifica que findBySku retorna el producto cuando el SKU existe en el repositorio.
  @Test
  @DisplayName("findBySku - retorna producto cuando el SKU existe")
  void findBySku_existingSku_returnsResponse() {
    when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(product));
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    ProductResponse result = productService.findBySku("SKU-001");

    assertThat(result.sku()).isEqualTo("SKU-001");
  }

  // Verifica que findBySku lanza ResourceNotFoundException cuando el SKU no existe.
  @Test
  @DisplayName("findBySku - lanza ResourceNotFoundException cuando el SKU no existe")
  void findBySku_missingSku_throwsNotFound() {
    when(productRepository.findBySku("NONEXISTENT")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.findBySku("NONEXISTENT"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("NONEXISTENT");
  }

  // ── update ────────────────────────────────────────────────────────────────

  // Verifica que se actualiza el producto correctamente cuando el SKU no cambia.
  @Test
  @DisplayName("update - actualiza producto correctamente cuando el SKU no cambia")
  void update_sameSku_updatesSuccessfully() {
    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "SKU-001", "Laptop Pro", "desc", new BigDecimal("1200.00"), 8, 2, true, null);
    when(productRepository.findById(1L)).thenReturn(Optional.of(product));
    when(productRepository.save(product)).thenReturn(product);
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    productService.update(1L, request);

    verify(productMapper).updateEntity(request, product);
    verify(productRepository).save(product);
  }

  // Verifica que cambiar el SKU a uno ya existente lanza ConflictException.
  @Test
  @DisplayName("update - lanza ConflictException cuando cambia SKU a uno existente")
  void update_skuChangedToDuplicate_throwsConflict() {
    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "SKU-999", "Laptop Pro", "desc", new BigDecimal("1200.00"), 8, 2, true, null);
    when(productRepository.findById(1L)).thenReturn(Optional.of(product));
    when(productRepository.existsBySku("SKU-999")).thenReturn(true);

    assertThatThrownBy(() -> productService.update(1L, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SKU-999");
  }

  // Verifica que actualizar un producto inexistente lanza ResourceNotFoundException.
  @Test
  @DisplayName("update - lanza ResourceNotFoundException cuando el producto no existe")
  void update_missingProduct_throwsNotFound() {
    ProductUpdateRequest request =
        new ProductUpdateRequest("SKU-001", "X", "d", new BigDecimal("1.00"), 1, 0, true, null);
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.update(99L, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── delete ────────────────────────────────────────────────────────────────

  // Verifica que delete realiza un soft delete poniendo active en false y persistiendo.
  @Test
  @DisplayName("delete - realiza soft delete poniendo active=false")
  void delete_existingProduct_setsActiveToFalse() {
    product.setActive(true);
    when(productRepository.findById(1L)).thenReturn(Optional.of(product));
    when(productRepository.save(product)).thenReturn(product);

    productService.delete(1L);

    assertThat(product.getActive()).isFalse();
    verify(productRepository).save(product);
  }

  // Verifica que eliminar un producto inexistente lanza ResourceNotFoundException.
  @Test
  @DisplayName("delete - lanza ResourceNotFoundException cuando el producto no existe")
  void delete_missingProduct_throwsNotFound() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.delete(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── findAll ───────────────────────────────────────────────────────────────

  // Verifica que findAll sin filtros retorna todos los productos paginados correctamente.
  @Test
  @DisplayName("findAll - aplica spec vacía cuando sin filtros y retorna página")
  void findAll_noFilters_returnsMappedPage() {
    PageRequest pageable = PageRequest.of(0, 20);
    Page<Product> page = new PageImpl<>(List.of(product));
    when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(productMapper.toResponse(product)).thenReturn(productResponse);

    Page<ProductResponse> result = productService.findAll(null, null, null, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).sku()).isEqualTo("SKU-001");
  }
}
