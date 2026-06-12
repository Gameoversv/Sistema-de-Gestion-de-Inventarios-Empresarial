package com.inventory.product.web;

import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Gestión de productos del inventario")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Listar productos con paginación, búsqueda y filtros")
  public Page<ProductResponse> list(
      @Parameter(description = "Búsqueda por nombre o SKU") @RequestParam(required = false)
          String search,
      @Parameter(description = "Filtrar por ID de categoría") @RequestParam(required = false)
          Long categoryId,
      @Parameter(description = "Filtrar por estado activo/inactivo") @RequestParam(required = false)
          Boolean active,
      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
    return productService.findAll(search, categoryId, active, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Obtener producto por ID")
  public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(productService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Crear nuevo producto")
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Reemplazar producto completo (todos los campos requeridos)")
  public ResponseEntity<ProductResponse> update(
      @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
    return ResponseEntity.ok(productService.update(id, request));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Actualización parcial de producto (solo campos enviados)")
  public ResponseEntity<ProductResponse> patch(
      @PathVariable Long id, @Valid @RequestBody ProductPatchRequest request) {
    return ResponseEntity.ok(productService.patch(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Desactivar producto (soft delete — activo=false)")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
