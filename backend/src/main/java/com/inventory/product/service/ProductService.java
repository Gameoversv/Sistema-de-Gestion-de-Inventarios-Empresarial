package com.inventory.product.service;

import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

  ProductResponse create(ProductCreateRequest request);

  ProductResponse findById(Long id);

  ProductResponse findBySku(String sku);

  Page<ProductResponse> findAll(String search, Long categoryId, Boolean active, Pageable pageable);

  ProductResponse update(Long id, ProductUpdateRequest request);

  ProductResponse patch(Long id, ProductPatchRequest request);

  void delete(Long id);
}
