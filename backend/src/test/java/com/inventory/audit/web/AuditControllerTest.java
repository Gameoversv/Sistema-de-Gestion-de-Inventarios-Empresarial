package com.inventory.audit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.audit.dto.AuditRevisionResponse;
import com.inventory.audit.service.StockAuditService;
import com.inventory.common.config.SecurityConfig;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import java.util.List;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean StockAuditService stockAuditService;

  // ── GET /api/audit/stock-movements ───────────────────────────────────────

  @Test
  void movementHistory_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/audit/stock-movements")).andExpect(status().isUnauthorized());
  }

  @Test
  void movementHistory_withStockManageScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void movementHistory_withStockViewScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void movementHistory_withAuditViewScope_returns200EmptyList() throws Exception {
    when(stockAuditService.findMovementHistory(isNull(), isNull(), isNull(), isNull()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void movementHistory_withAuditScope_returnsRevisionList() throws Exception {
    var now = Instant.now();
    var revision =
        new AuditRevisionResponse(
            1,
            now,
            "auditor",
            RevisionType.ADD,
            10L,
            1L,
            "SKU-001",
            "Widget",
            MovementType.IN,
            5,
            0,
            5,
            "auditor",
            "stock arrival");
    when(stockAuditService.findMovementHistory(isNull(), isNull(), isNull(), isNull()))
        .thenReturn(List.of(revision));

    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].revisionNumber").value(1))
        .andExpect(jsonPath("$[0].sku").value("SKU-001"))
        .andExpect(jsonPath("$[0].movementType").value("IN"))
        .andExpect(jsonPath("$[0].quantityBefore").value(0))
        .andExpect(jsonPath("$[0].quantityAfter").value(5))
        .andExpect(jsonPath("$[0].revisedBy").value("auditor"));
  }

  @Test
  void movementHistory_withProductIdFilter_passesFilterToService() throws Exception {
    when(stockAuditService.findMovementHistory(eq(99L), isNull(), isNull(), isNull()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .param("productId", "99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isOk());

    verify(stockAuditService).findMovementHistory(eq(99L), isNull(), isNull(), isNull());
  }

  @Test
  void movementHistory_withUsernameFilter_passesFilterToService() throws Exception {
    when(stockAuditService.findMovementHistory(isNull(), eq("jdoe"), isNull(), isNull()))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .param("username", "jdoe")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isOk());

    verify(stockAuditService).findMovementHistory(isNull(), eq("jdoe"), isNull(), isNull());
  }

  @Test
  void movementHistory_withDateRangeFilter_passesFilterToService() throws Exception {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");

    when(stockAuditService.findMovementHistory(
            isNull(), isNull(), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/audit/stock-movements")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isOk());

    verify(stockAuditService).findMovementHistory(isNull(), isNull(), eq(from), eq(to));
  }
}
