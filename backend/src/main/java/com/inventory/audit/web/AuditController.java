package com.inventory.audit.web;

import com.inventory.audit.dto.AuditRevisionResponse;
import com.inventory.audit.service.StockAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(name = "Audit", description = "Historial de revisiones Envers — requiere audit:view")
@ApiResponse(
    responseCode = "401",
    description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Scope audit:view requerido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public class AuditController {

  private final StockAuditService stockAuditService;

  @GetMapping("/stock-movements")
  @PreAuthorize("hasAuthority('SCOPE_audit:view')")
  @Operation(
      summary = "Historial de revisiones de movimientos de stock (Envers)",
      description = "Filtros opcionales: productId, username, from, to.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Lista de revisiones de auditoría",
            content = @Content(schema = @Schema(implementation = AuditRevisionResponse.class)))
      })
  public List<AuditRevisionResponse> movementHistory(
      @Parameter(description = "Filtrar por ID de producto") @RequestParam(required = false)
          Long productId,
      @Parameter(description = "Filtrar por nombre de usuario") @RequestParam(required = false)
          String username,
      @Parameter(description = "Fecha inicio (ISO-8601)") @RequestParam(required = false)
          Instant from,
      @Parameter(description = "Fecha fin (ISO-8601)") @RequestParam(required = false) Instant to) {
    return stockAuditService.findMovementHistory(productId, username, from, to);
  }
}
