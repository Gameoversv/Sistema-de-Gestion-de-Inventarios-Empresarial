package com.inventory.product.service;

import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

  ProductResponse create(ProductCreateRequest request);

  ProductResponse findById(Long id);

  ProductResponse findBySku(String sku);

  Page<ProductResponse> findAll(Pageable pageable);

  Page<ProductResponse> search(String query, Pageable pageable);

  ProductResponse update(Long id, ProductUpdateRequest request);

  void delete(Long id);
}
