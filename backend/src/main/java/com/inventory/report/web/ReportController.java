package com.inventory.report.web;

import com.inventory.report.dto.CriticalStockResponse;
import com.inventory.report.dto.DashboardMetricsResponse;
import com.inventory.report.dto.LowStockReportResponse;
import com.inventory.report.dto.RecentMovementsResponse;
import com.inventory.report.dto.StockSummaryResponse;
import com.inventory.report.dto.TopProductsResponse;
import com.inventory.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "keycloak")
@Tag(name = "Reports", description = "Reportes del dashboard — resumen de stock y alertas")
@ApiResponse(
    responseCode = "401",
    description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "403",
    description = "Scope report:view requerido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
/**
 * Controlador REST para los reportes del dashboard de inventario ({@code /api/reports}). Expone
 * endpoints de solo lectura para resumen de stock, alertas, top productos, métricas globales y
 * movimientos recientes. Todos requieren scope {@code report:view}.
 */
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/stock-summary")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(summary = "Resumen de inventario agrupado por categoría")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Resumen de stock generado correctamente",
            content = @Content(schema = @Schema(implementation = StockSummaryResponse.class)))
      })
  public StockSummaryResponse stockSummary() {
    return reportService.stockSummary();
  }

  @GetMapping("/low-stock")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(
      summary = "Reporte de productos bajo stock mínimo",
      description =
          "Devuelve productos cuyo stock ≤ umbral. "
              + "Si threshold=0 usa el minimumStock configurado por producto.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Reporte generado correctamente",
            content = @Content(schema = @Schema(implementation = LowStockReportResponse.class)))
      })
  public LowStockReportResponse lowStock(
      @Parameter(
              description = "Umbral de stock (0 = usar minimumStock por producto)",
              example = "5")
          @RequestParam(defaultValue = "0")
          int threshold) {
    return reportService.lowStockAlert(threshold);
  }

  @GetMapping("/critical-stock")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(
      summary = "Productos activos con stock en cero",
      description = "Devuelve todos los productos activos cuyo stock actual es exactamente 0.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Listado de productos sin stock",
            content = @Content(schema = @Schema(implementation = CriticalStockResponse.class)))
      })
  public CriticalStockResponse criticalStock() {
    return reportService.criticalStock();
  }

  @GetMapping("/top-products")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(
      summary = "Top productos por valor de inventario o cantidad de stock",
      description =
          "Devuelve los N productos activos con mayor inventario. "
              + "metric=value ordena por precio×stock; metric=quantity por unidades en stock.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Ranking calculado correctamente",
            content = @Content(schema = @Schema(implementation = TopProductsResponse.class)))
      })
  public TopProductsResponse topProducts(
      @Parameter(description = "Número máximo de productos a devolver", example = "10")
          @RequestParam(defaultValue = "10")
          int limit,
      @Parameter(
              description = "Métrica de ordenamiento: value (precio×stock) | quantity (unidades)",
              example = "value")
          @RequestParam(defaultValue = "value")
          String metric) {
    return reportService.topProducts(limit, metric);
  }

  @GetMapping("/dashboard-metrics")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(
      summary = "Métricas generales del inventario",
      description =
          "Agrega en una sola respuesta los contadores clave del inventario: productos, categorías,"
              + " movimientos, valor total, alertas de stock y último movimiento.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Métricas calculadas correctamente",
            content = @Content(schema = @Schema(implementation = DashboardMetricsResponse.class)))
      })
  public DashboardMetricsResponse dashboardMetrics() {
    return reportService.dashboardMetrics();
  }

  @GetMapping("/recent-movements")
  @PreAuthorize("hasAuthority('SCOPE_report:view')")
  @Operation(
      summary = "Movimientos de stock más recientes",
      description =
          "Devuelve los N movimientos de stock más recientes en orden cronológico inverso.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Movimientos recientes devueltos",
            content = @Content(schema = @Schema(implementation = RecentMovementsResponse.class)))
      })
  public RecentMovementsResponse recentMovements(
      @Parameter(description = "Número de movimientos a devolver (máximo 100)", example = "20")
          @RequestParam(defaultValue = "20")
          int limit) {
    return reportService.recentMovements(limit);
  }
}
