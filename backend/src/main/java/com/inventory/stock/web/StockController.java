package com.inventory.stock.web;

import com.inventory.product.dto.ProductResponse;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import com.inventory.stock.service.StockService;
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
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(name = "Stock", description = "Control de inventario — movimientos y alertas")
@ApiResponse(
    responseCode = "401",
    description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Scope insuficiente",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
/**
 * Controlador REST para el control de inventario ({@code /api/stock}). Permite registrar
 * movimientos (IN/OUT/ADJUSTMENT), listar movimientos con filtros opcionales y consultar alertas de
 * stock bajo. El registro requiere scope {@code stock:manage}; la lectura acepta también {@code
 * stock:view}.
 */
public class StockController {

  private final StockService stockService;

  @PostMapping("/movements")
  @PreAuthorize("hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Registrar movimiento de inventario (IN / OUT / ADJUSTMENT)")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Movimiento registrado",
            content =
                @Content(
                    schema = @Schema(implementation = StockMovementResponse.class),
                    examples =
                        @ExampleObject(
                            name = "entrada",
                            summary = "Entrada de 50 unidades",
                            value =
                                """
                                {
                                  "id": 100,
                                  "productId": 1,
                                  "sku": "LAPTOP-001",
                                  "productName": "Laptop Dell XPS 15",
                                  "type": "IN",
                                  "quantity": 50,
                                  "quantityBefore": 10,
                                  "quantityAfter": 60,
                                  "reason": "Reposición mensual",
                                  "referenceId": "PO-2024-001",
                                  "performedBy": "admin"
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
      })
  public ResponseEntity<StockMovementResponse> register(
      @Valid @RequestBody StockMovementRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stockService.registerMovement(request, authentication));
  }

  @GetMapping("/movements")
  @PreAuthorize("hasAuthority('SCOPE_stock:view') or hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Listar movimientos con filtros opcionales")
  @ApiResponse(responseCode = "200", description = "Lista paginada de movimientos de stock")
  public Page<StockMovementResponse> list(
      @Parameter(description = "Filtrar por ID de producto") @RequestParam(required = false)
          Long productId,
      @Parameter(description = "Filtrar por tipo: IN, OUT, ADJUSTMENT")
          @RequestParam(required = false)
          MovementType type,
      @Parameter(description = "Fecha inicio (ISO-8601)") @RequestParam(required = false)
          Instant from,
      @Parameter(description = "Fecha fin (ISO-8601)") @RequestParam(required = false) Instant to,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return stockService.getMovements(productId, type, from, to, pageable);
  }

  @GetMapping("/alerts")
  @PreAuthorize("hasAuthority('SCOPE_stock:view') or hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Productos bajo stock mínimo")
  @ApiResponse(responseCode = "200", description = "Lista de productos con stock insuficiente")
  public List<ProductResponse> alerts() {
    return stockService.getLowStockAlerts();
  }
}
