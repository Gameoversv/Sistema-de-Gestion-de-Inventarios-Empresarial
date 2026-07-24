package com.inventory.product.service;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import com.inventory.product.repository.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de productos. Gestiona CRUD con validación de SKU único, filtrado
 * dinámico mediante {@link com.inventory.product.repository.ProductSpecification}, conversión de
 * entidades con MapStruct y soft delete (activo=false en lugar de borrado físico).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

  private static final String NOT_FOUND_BY_SKU = "Product not found by SKU: ";

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ProductMapper productMapper;

  /**
   * Excepción de producto inexistente por id.
   *
   * <p>El mismo mensaje estaba escrito en cuatro sitios. Concentrarlo aquí evita que una búsqueda
   * futura por ese texto —en logs o en tests— encuentre solo algunas de las rutas que lo emiten.
   */
  private static ResourceNotFoundException productNotFound(Long id) {
    return new ResourceNotFoundException("Product not found: " + id);
  }

  @Override
  @Transactional
  public ProductResponse create(ProductCreateRequest request) {
    if (productRepository.existsBySku(request.sku())) {
      throw new ConflictException("SKU already exists: " + request.sku());
    }
    Product product = productMapper.toEntity(request);
    product.setMinimumStock(request.minimumStock() != null ? request.minimumStock() : 0);
    product.setActive(request.active() != null ? request.active() : Boolean.TRUE);
    if (request.categoryId() != null) {
      product.setCategory(resolveCategory(request.categoryId()));
    }
    return productMapper.toResponse(productRepository.save(product));
  }

  @Override
  public ProductResponse findById(Long id) {
    return productMapper.toResponse(
        productRepository.findById(id).orElseThrow(() -> productNotFound(id)));
  }

  @Override
  public ProductResponse findBySku(String sku) {
    return productMapper.toResponse(
        productRepository
            .findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_BY_SKU + sku)));
  }

  @Override
  public Page<ProductResponse> findAll(
      String search, Long categoryId, Boolean active, Pageable pageable) {
    Specification<Product> spec = Specification.where(null);
    if (search != null && !search.isBlank()) {
      spec = spec.and(ProductSpecification.nameOrSkuContains(search));
    }
    if (categoryId != null) {
      spec = spec.and(ProductSpecification.hasCategory(categoryId));
    }
    if (active != null) {
      spec = spec.and(ProductSpecification.isActive(active));
    }
    return productRepository.findAll(spec, pageable).map(productMapper::toResponse);
  }

  @Override
  @Transactional
  public ProductResponse update(Long id, ProductUpdateRequest request) {
    Product existing = productRepository.findById(id).orElseThrow(() -> productNotFound(id));
    if (!existing.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
      throw new ConflictException("SKU already exists: " + request.sku());
    }
    productMapper.updateEntity(request, existing);
    existing.setCategory(
        request.categoryId() != null ? resolveCategory(request.categoryId()) : null);
    return productMapper.toResponse(productRepository.save(existing));
  }

  @Override
  @Transactional
  public ProductResponse patch(Long id, ProductPatchRequest request) {
    Product existing = productRepository.findById(id).orElseThrow(() -> productNotFound(id));
    if (request.sku() != null
        && !request.sku().equals(existing.getSku())
        && productRepository.existsBySku(request.sku())) {
      throw new ConflictException("SKU already exists: " + request.sku());
    }
    productMapper.patchEntity(request, existing);
    if (request.categoryId() != null) {
      existing.setCategory(resolveCategory(request.categoryId()));
    }
    return productMapper.toResponse(productRepository.save(existing));
  }

  @Override
  @Transactional
  public void delete(Long id) {
    Product product = productRepository.findById(id).orElseThrow(() -> productNotFound(id));
    product.setActive(Boolean.FALSE);
    productRepository.save(product);
  }

  private Category resolveCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
  }
}
