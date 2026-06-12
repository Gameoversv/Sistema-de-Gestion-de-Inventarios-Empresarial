package com.inventory.product.service;

import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import java.util.List;

public interface CategoryService {

  CategoryResponse create(CategoryCreateRequest request);

  CategoryResponse findById(Long id);

  List<CategoryResponse> findAll();

  CategoryResponse update(Long id, CategoryUpdateRequest request);

  void delete(Long id);
}
