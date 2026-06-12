package com.inventory.stock.web;

import com.inventory.product.dto.ProductResponse;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import com.inventory.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Control de inventario — movimientos y alertas")
public class StockController {

  private final StockService stockService;

  @PostMapping("/movements")
  @PreAuthorize("hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Registrar movimiento de inventario (IN / OUT / ADJUSTMENT)")
  public ResponseEntity<StockMovementResponse> register(
      @Valid @RequestBody StockMovementRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(stockService.registerMovement(request, authentication));
  }

  @GetMapping("/movements")
  @PreAuthorize("hasAuthority('SCOPE_stock:view') or hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Listar movimientos con filtros opcionales")
  public Page<StockMovementResponse> list(
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) MovementType type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return stockService.getMovements(productId, type, from, to, pageable);
  }

  @GetMapping("/alerts")
  @PreAuthorize("hasAuthority('SCOPE_stock:view') or hasAuthority('SCOPE_stock:manage')")
  @Operation(summary = "Productos bajo stock mínimo")
  public List<ProductResponse> alerts() {
    return stockService.getLowStockAlerts();
  }
}
