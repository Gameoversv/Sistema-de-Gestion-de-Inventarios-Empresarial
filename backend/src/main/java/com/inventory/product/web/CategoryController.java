package com.inventory.product.web;

import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import com.inventory.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(name = "Categories", description = "Gestión de categorías de productos")
@ApiResponse(
    responseCode = "401",
    description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Scope insuficiente",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
/**
 * Controlador REST para la gestión de categorías de productos ({@code /categories}). Las
 * operaciones de lectura requieren scope {@code product:view} o {@code product:manage}; las de
 * escritura requieren solo {@code product:manage}.
 */
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Listar todas las categorías")
  @ApiResponse(responseCode = "200", description = "Lista de categorías")
  public List<CategoryResponse> list() {
    return categoryService.findAll();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Obtener categoría por ID")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Categoría encontrada",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Categoría no encontrada",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(categoryService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Crear nueva categoría")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Categoría creada",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CategoryCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Actualizar categoría")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Categoría actualizada",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Categoría no encontrada",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<CategoryResponse> update(
      @PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
    return ResponseEntity.ok(categoryService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Eliminar categoría")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Categoría eliminada"),
        @ApiResponse(
            responseCode = "404",
            description = "Categoría no encontrada",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
