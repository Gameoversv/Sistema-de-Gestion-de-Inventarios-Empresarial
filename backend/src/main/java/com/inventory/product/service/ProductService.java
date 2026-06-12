package com.inventory.product.service;

import com.inventory.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

  Product create(Product product);

  Product findById(Long id);

  Product findBySku(String sku);

  Page<Product> findAll(Pageable pageable);

  Page<Product> search(String query, Pageable pageable);

  Product update(Long id, Product product);

  void delete(Long id);
}
