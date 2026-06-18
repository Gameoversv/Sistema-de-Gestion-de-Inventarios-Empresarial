package com.inventory.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.config.SecurityConfig;
import com.inventory.report.dto.*;
import com.inventory.report.service.ReportService;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerExtendedTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean ReportService reportService;

  // ── GET /api/reports/critical-stock ──────────────────────────────────────

  // Verifica que acceder a critical-stock sin autenticación retorna 401.
  @Test
  void criticalStock_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/critical-stock")).andExpect(status().isUnauthorized());
  }

  // Verifica que un scope insuficiente (stock:view) retorna 403 en critical-stock.
  @Test
  void criticalStock_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/critical-stock")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope report:view se obtiene 200 con los productos en stock crítico.
  @Test
  void criticalStock_withReportViewScope_returns200WithBody() throws Exception {
    var item = new LowStockItemDto(1L, "SKU-Z", "Widget", 0, 5, 5, "General");
    var response = new CriticalStockResponse(1, List.of(item));
    when(reportService.criticalStock()).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/critical-stock")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.products[0].sku").value("SKU-Z"))
        .andExpect(jsonPath("$.products[0].currentStock").value(0))
        .andExpect(jsonPath("$.products[0].deficit").value(5));
  }

  // ── GET /api/reports/top-products ─────────────────────────────────────────

  // Verifica que acceder a top-products sin autenticación retorna 401.
  @Test
  void topProducts_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/top-products")).andExpect(status().isUnauthorized());
  }

  // Verifica que un scope insuficiente (audit:view) retorna 403 en top-products.
  @Test
  void topProducts_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/top-products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope report:view y parámetros por defecto se retorna 200 con el ranking.
  @Test
  void topProducts_withReportViewScope_defaultParams_returns200() throws Exception {
    var item =
        new TopProductDto(
            1L,
            "LAPTOP-001",
            "Laptop Dell XPS 15",
            50,
            BigDecimal.valueOf(1299.99),
            BigDecimal.valueOf(64999.50),
            "Electrónica");
    var response = new TopProductsResponse(10, "value", List.of(item));
    when(reportService.topProducts(10, "value")).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/top-products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(10))
        .andExpect(jsonPath("$.metric").value("value"))
        .andExpect(jsonPath("$.products[0].sku").value("LAPTOP-001"))
        .andExpect(jsonPath("$.products[0].stock").value(50));
  }

  // Verifica que los parámetros limit y metric se propagan correctamente al servicio.
  @Test
  void topProducts_withCustomParams_passesParamsToService() throws Exception {
    var response = new TopProductsResponse(5, "quantity", List.of());
    when(reportService.topProducts(5, "quantity")).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/top-products")
                .param("limit", "5")
                .param("metric", "quantity")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metric").value("quantity"));

    verify(reportService).topProducts(5, "quantity");
  }

  // ── GET /api/reports/dashboard-metrics ───────────────────────────────────

  // Verifica que acceder a dashboard-metrics sin autenticación retorna 401.
  @Test
  void dashboardMetrics_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/dashboard-metrics")).andExpect(status().isUnauthorized());
  }

  // Verifica que un scope insuficiente (product:view) retorna 403 en dashboard-metrics.
  @Test
  void dashboardMetrics_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/dashboard-metrics")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope report:view se retorna 200 con todas las métricas del dashboard.
  @Test
  void dashboardMetrics_withReportViewScope_returns200WithAllFields() throws Exception {
    var response =
        new DashboardMetricsResponse(
            120,
            115,
            5,
            8L,
            1450L,
            BigDecimal.valueOf(750000),
            12,
            3,
            Instant.parse("2026-05-29T10:00:00Z"));
    when(reportService.dashboardMetrics()).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/dashboard-metrics")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalProducts").value(120))
        .andExpect(jsonPath("$.activeProducts").value(115))
        .andExpect(jsonPath("$.inactiveProducts").value(5))
        .andExpect(jsonPath("$.totalCategories").value(8))
        .andExpect(jsonPath("$.totalStockMovements").value(1450))
        .andExpect(jsonPath("$.lowStockCount").value(12))
        .andExpect(jsonPath("$.criticalStockCount").value(3));
  }

  // ── GET /api/reports/recent-movements ─────────────────────────────────────

  // Verifica que acceder a recent-movements sin autenticación retorna 401.
  @Test
  void recentMovements_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/recent-movements")).andExpect(status().isUnauthorized());
  }

  // Verifica que un scope insuficiente (stock:manage) retorna 403 en recent-movements.
  @Test
  void recentMovements_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/recent-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope report:view se retorna 200 con los movimientos recientes mapeados.
  @Test
  void recentMovements_withReportViewScope_returns200WithBody() throws Exception {
    var movement =
        new RecentMovementDto(
            42L,
            1L,
            "LAPTOP-001",
            "Laptop Dell XPS 15",
            MovementType.IN,
            20,
            30,
            50,
            "admin",
            Instant.parse("2026-05-29T09:00:00Z"));
    var response = new RecentMovementsResponse(20, 1, List.of(movement));
    when(reportService.recentMovements(20)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/recent-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(20))
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.movements[0].sku").value("LAPTOP-001"))
        .andExpect(jsonPath("$.movements[0].type").value("IN"))
        .andExpect(jsonPath("$.movements[0].quantity").value(20))
        .andExpect(jsonPath("$.movements[0].performedBy").value("admin"));
  }

  // Verifica que el parámetro limit se propaga correctamente al servicio de reportes.
  @Test
  void recentMovements_withCustomLimit_passesLimitToService() throws Exception {
    var response = new RecentMovementsResponse(5, 0, List.of());
    when(reportService.recentMovements(5)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/recent-movements")
                .param("limit", "5")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(5));

    verify(reportService).recentMovements(5);
  }
}
