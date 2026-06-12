package com.inventory.product.web;

import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import com.inventory.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Gestión de categorías de productos")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Listar todas las categorías")
  public List<CategoryResponse> list() {
    return categoryService.findAll();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Obtener categoría por ID")
  public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(categoryService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Crear nueva categoría")
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CategoryCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Actualizar categoría")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
    return ResponseEntity.ok(categoryService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Eliminar categoría")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
