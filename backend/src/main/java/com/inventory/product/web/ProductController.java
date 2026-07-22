package com.inventory.product.web;

import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(name = "Products", description = "Gestión de productos del inventario")
@ApiResponse(
    responseCode = "401",
    description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Scope insuficiente",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
/**
 * Controlador REST para la gestión de productos del inventario ({@code /products}). Soporta
 * paginación, búsqueda por nombre/SKU, filtros por categoría y estado activo, CRUD completo
 * (incluyendo PATCH parcial) y soft delete. Requiere autenticación JWT de Keycloak.
 */
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @PreAuthorize("hasAuthority('SCOPE_product:view') or hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Listar productos con paginación, búsqueda y filtros")
  @ApiResponse(responseCode = "200", description = "Lista de productos paginada")
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
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Producto encontrado",
            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(productService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Crear nuevo producto")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Producto creado",
            content =
                @Content(
                    schema = @Schema(implementation = ProductResponse.class),
                    examples =
                        @ExampleObject(
                            name = "laptop",
                            summary = "Ejemplo — laptop",
                            value =
                                """
                                {
                                  "id": 1,
                                  "sku": "LAPTOP-001",
                                  "name": "Laptop Dell XPS 15",
                                  "price": 1299.99,
                                  "stock": 10,
                                  "minimumStock": 2,
                                  "active": true,
                                  "categoryId": 1,
                                  "categoryName": "Electrónica"
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "SKU ya existe",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Reemplazar producto completo (todos los campos requeridos)")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Producto actualizado",
            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ProductResponse> update(
      @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
    return ResponseEntity.ok(productService.update(id, request));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Actualización parcial de producto (solo campos enviados)")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Producto actualizado",
            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<ProductResponse> patch(
      @PathVariable Long id, @Valid @RequestBody ProductPatchRequest request) {
    return ResponseEntity.ok(productService.patch(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('SCOPE_product:manage')")
  @Operation(summary = "Desactivar producto (soft delete — activo=false)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Producto desactivado"),
        @ApiResponse(
            responseCode = "404",
            description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
