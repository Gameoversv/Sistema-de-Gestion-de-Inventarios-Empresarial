package com.inventory.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.config.SecurityConfig;
import com.inventory.report.dto.*;
import com.inventory.report.service.ReportService;
import java.math.BigDecimal;
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
class ReportControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean ReportService reportService;

  // ── GET /api/reports/stock-summary ────────────────────────────────────────

  @Test
  void stockSummary_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/stock-summary")).andExpect(status().isUnauthorized());
  }

  @Test
  void stockSummary_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/stock-summary")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void stockSummary_withReportViewScope_returns200WithBody() throws Exception {
    var response =
        new StockSummaryResponse(
            10,
            9,
            2,
            BigDecimal.valueOf(5000),
            List.of(new CategoryStockDto("Electrónica", 9L, 80L, BigDecimal.valueOf(5000))));
    when(reportService.stockSummary()).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/stock-summary")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalProducts").value(10))
        .andExpect(jsonPath("$.activeProducts").value(9))
        .andExpect(jsonPath("$.lowStockProducts").value(2))
        .andExpect(jsonPath("$.byCategory[0].categoryName").value("Electrónica"))
        .andExpect(jsonPath("$.byCategory[0].productCount").value(9));
  }

  @Test
  void stockSummary_withReportViewScope_emptyCategories_returns200() throws Exception {
    var response = new StockSummaryResponse(0, 0, 0, BigDecimal.ZERO, List.of());
    when(reportService.stockSummary()).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/stock-summary")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalProducts").value(0))
        .andExpect(jsonPath("$.byCategory").isArray())
        .andExpect(jsonPath("$.byCategory").isEmpty());
  }

  // ── GET /api/reports/low-stock ────────────────────────────────────────────

  @Test
  void lowStock_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/reports/low-stock")).andExpect(status().isUnauthorized());
  }

  @Test
  void lowStock_withoutReportViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/low-stock")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void lowStock_withReportViewScope_defaultThreshold_returns200WithItems() throws Exception {
    var item = new LowStockItemDto(1L, "SKU-X", "Widget", 2, 10, 8, "General");
    var response = new LowStockReportResponse(0, 1, List.of(item));
    when(reportService.lowStockAlert(0)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/low-stock")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].sku").value("SKU-X"))
        .andExpect(jsonPath("$.items[0].currentStock").value(2))
        .andExpect(jsonPath("$.items[0].deficit").value(8));
  }

  @Test
  void lowStock_withThresholdParam_passesThresholdToService() throws Exception {
    var response = new LowStockReportResponse(5, 0, List.of());
    when(reportService.lowStockAlert(5)).thenReturn(response);

    mockMvc
        .perform(
            get("/api/reports/low-stock")
                .param("threshold", "5")
                .with(
                    jwt()
                        .jwt(j -> j.subject("analyst"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.threshold").value(5));

    verify(reportService).lowStockAlert(5);
  }
}
