package com.inventory.product.service;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ProductMapper productMapper;

  @Override
  @Transactional
  public ProductResponse create(ProductCreateRequest request) {
    if (productRepository.existsBySku(request.sku())) {
      throw new ConflictException("SKU already exists: " + request.sku());
    }
    Product product = productMapper.toEntity(request);
    if (request.categoryId() != null) {
      product.setCategory(resolveCategory(request.categoryId()));
    }
    return productMapper.toResponse(productRepository.save(product));
  }

  @Override
  public ProductResponse findById(Long id) {
    return productMapper.toResponse(
        productRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id)));
  }

  @Override
  public ProductResponse findBySku(String sku) {
    return productMapper.toResponse(
        productRepository
            .findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found by SKU: " + sku)));
  }

  @Override
  public Page<ProductResponse> findAll(Pageable pageable) {
    return productRepository.findAll(pageable).map(productMapper::toResponse);
  }

  @Override
  public Page<ProductResponse> search(String query, Pageable pageable) {
    return productRepository.search(query, pageable).map(productMapper::toResponse);
  }

  @Override
  @Transactional
  public ProductResponse update(Long id, ProductUpdateRequest request) {
    Product existing =
        productRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
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
  public void delete(Long id) {
    if (!productRepository.existsById(id)) {
      throw new ResourceNotFoundException("Product not found: " + id);
    }
    productRepository.deleteById(id);
  }

  private Category resolveCategory(Long categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
  }
}
