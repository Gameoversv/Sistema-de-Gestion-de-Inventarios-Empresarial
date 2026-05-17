package com.inventory.product.service;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Product;
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

  @Override
  @Transactional
  public Product create(Product product) {
    if (productRepository.existsBySku(product.getSku())) {
      throw new ConflictException("SKU already exists: " + product.getSku());
    }
    return productRepository.save(product);
  }

  @Override
  public Product findById(Long id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
  }

  @Override
  public Product findBySku(String sku) {
    return productRepository
        .findBySku(sku)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found by SKU: " + sku));
  }

  @Override
  public Page<Product> findAll(Pageable pageable) {
    return productRepository.findAll(pageable);
  }

  @Override
  public Page<Product> search(String query, Pageable pageable) {
    return productRepository.search(query, pageable);
  }

  @Override
  @Transactional
  public Product update(Long id, Product updated) {
    Product existing = findById(id);
    if (!existing.getSku().equals(updated.getSku())
        && productRepository.existsBySku(updated.getSku())) {
      throw new ConflictException("SKU already exists: " + updated.getSku());
    }
    existing.setSku(updated.getSku());
    existing.setName(updated.getName());
    existing.setDescription(updated.getDescription());
    existing.setPrice(updated.getPrice());
    existing.setStock(updated.getStock());
    existing.setCategory(updated.getCategory());
    return productRepository.save(existing);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (!productRepository.existsById(id)) {
      throw new ResourceNotFoundException("Product not found: " + id);
    }
    productRepository.deleteById(id);
  }
}
