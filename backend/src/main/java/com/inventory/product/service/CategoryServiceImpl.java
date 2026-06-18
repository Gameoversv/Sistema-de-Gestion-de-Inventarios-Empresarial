package com.inventory.product.service;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import com.inventory.product.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de categorías. Aplica CRUD sobre la entidad Category, validando que
 * el nombre sea único antes de crear o actualizar. Las escrituras se realizan en transacciones
 * independientes; las lecturas son de solo lectura.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;

  @Override
  @Transactional
  public CategoryResponse create(CategoryCreateRequest request) {
    if (categoryRepository.existsByName(request.name())) {
      throw new ConflictException("Category name already exists: " + request.name());
    }
    Category category =
        Category.builder().name(request.name()).description(request.description()).build();
    return toResponse(categoryRepository.save(category));
  }

  @Override
  public CategoryResponse findById(Long id) {
    return toResponse(
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)));
  }

  @Override
  public List<CategoryResponse> findAll() {
    return categoryRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Override
  @Transactional
  public CategoryResponse update(Long id, CategoryUpdateRequest request) {
    Category existing =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    if (!existing.getName().equals(request.name())
        && categoryRepository.existsByName(request.name())) {
      throw new ConflictException("Category name already exists: " + request.name());
    }
    existing.setName(request.name());
    existing.setDescription(request.description());
    return toResponse(categoryRepository.save(existing));
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (!categoryRepository.existsById(id)) {
      throw new ResourceNotFoundException("Category not found: " + id);
    }
    categoryRepository.deleteById(id);
  }

  private CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getDescription(),
        category.getCreatedAt(),
        category.getUpdatedAt());
  }
}
