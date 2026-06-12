package com.inventory.stock.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.common.config.SecurityConfig;
import com.inventory.product.dto.ProductResponse;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import com.inventory.stock.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)
class StockControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean StockService stockService;

  // ── POST /api/stock/movements ─────────────────────────────────────────────

  @Test
  void registerMovement_anonymous_returns401() throws Exception {
    mockMvc.perform(post("/api/stock/movements")).andExpect(status().isUnauthorized());
  }

  @Test
  void registerMovement_insufficientScope_returns403() throws Exception {
    var request = new StockMovementRequest(1L, MovementType.IN, 10, null, null);

    mockMvc
        .perform(
            post("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void registerMovement_validRequest_returns201WithBody() throws Exception {
    var request = new StockMovementRequest(1L, MovementType.IN, 10, "purchase", "REF-001");
    var response =
        new StockMovementResponse(
            42L,
            1L,
            "SKU-001",
            "Widget",
            MovementType.IN,
            10,
            90,
            100,
            "purchase",
            "REF-001",
            "manager",
            Instant.now());

    when(stockService.registerMovement(any(StockMovementRequest.class), any(Authentication.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager").claim("preferred_username", "manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.sku").value("SKU-001"))
        .andExpect(jsonPath("$.type").value("IN"))
        .andExpect(jsonPath("$.quantityBefore").value(90))
        .andExpect(jsonPath("$.quantityAfter").value(100))
        .andExpect(jsonPath("$.performedBy").value("manager"));
  }

  @Test
  void registerMovement_nullProductId_returns400() throws Exception {
    var invalidRequest = new StockMovementRequest(null, MovementType.IN, 10, null, null);

    mockMvc
        .perform(
            post("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerMovement_negativeQuantity_returns400() throws Exception {
    var invalidRequest = new StockMovementRequest(1L, MovementType.OUT, -5, null, null);

    mockMvc
        .perform(
            post("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  // ── GET /api/stock/movements ──────────────────────────────────────────────

  @Test
  void listMovements_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/stock/movements")).andExpect(status().isUnauthorized());
  }

  @Test
  void listMovements_withStockView_returns200() throws Exception {
    when(stockService.getMovements(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void listMovements_withStockManage_returns200() throws Exception {
    when(stockService.getMovements(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/stock/movements")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage"))))
        .andExpect(status().isOk());
  }

  @Test
  void listMovements_withTypeFilter_passesFilterToService() throws Exception {
    when(stockService.getMovements(eq(1L), eq(MovementType.IN), isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/stock/movements")
                .param("productId", "1")
                .param("type", "IN")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isOk());

    verify(stockService).getMovements(eq(1L), eq(MovementType.IN), isNull(), isNull(), any());
  }

  // ── GET /api/stock/alerts ─────────────────────────────────────────────────

  @Test
  void alerts_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/api/stock/alerts")).andExpect(status().isUnauthorized());
  }

  @Test
  void alerts_withStockView_returns200WithList() throws Exception {
    var product =
        new ProductResponse(
            1L,
            "SKU-LOW",
            "Low Widget",
            null,
            BigDecimal.TEN,
            2,
            10,
            true,
            1L,
            "General",
            Instant.now(),
            Instant.now());
    when(stockService.getLowStockAlerts()).thenReturn(List.of(product));

    mockMvc
        .perform(
            get("/api/stock/alerts")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_stock:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sku").value("SKU-LOW"))
        .andExpect(jsonPath("$[0].stock").value(2));
  }

  @Test
  void alerts_withAuditScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/stock/alerts")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isForbidden());
  }
}
