package com.inventory.audit.web;

import com.inventory.audit.dto.AuditRevisionResponse;
import com.inventory.audit.service.StockAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Historial de revisiones Envers — requiere audit:view")
public class AuditController {

  private final StockAuditService stockAuditService;

  @GetMapping("/stock-movements")
  @PreAuthorize("hasAuthority('SCOPE_audit:view')")
  @Operation(
      summary =
          "Historial de revisiones de movimientos de stock (Envers). "
              + "Filtros opcionales: productId, username, from, to.")
  public List<AuditRevisionResponse> movementHistory(
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return stockAuditService.findMovementHistory(productId, username, from, to);
  }
}
