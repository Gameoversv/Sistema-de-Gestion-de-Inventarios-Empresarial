package com.inventory.product.service;

import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import java.util.List;

/**
 * Contrato del servicio de categorías. Define las operaciones CRUD disponibles: crear, consultar
 * por ID, listar todas, actualizar y eliminar una categoría.
 */
public interface CategoryService {

  CategoryResponse create(CategoryCreateRequest request);

  CategoryResponse findById(Long id);

  List<CategoryResponse> findAll();

  CategoryResponse update(Long id, CategoryUpdateRequest request);

  void delete(Long id);
}
