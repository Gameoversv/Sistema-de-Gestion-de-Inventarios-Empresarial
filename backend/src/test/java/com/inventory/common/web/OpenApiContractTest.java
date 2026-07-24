package com.inventory.common.web;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.inventory.common.config.SecurityConfig;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.service.CategoryService;
import com.inventory.product.service.ProductService;
import com.inventory.product.web.CategoryController;
import com.inventory.product.web.ProductController;
import com.inventory.report.dto.StockSummaryResponse;
import com.inventory.report.service.ReportService;
import com.inventory.report.web.ReportController;
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

/**
 * TEST-2 — Contract testing: verifica que las respuestas reales de la API cumplen el contrato
 * OpenAPI publicado en {@code docs/api/openapi.yaml}. Si un controlador deja de encajar con el
 * esquema documentado (un campo renombrado, un tipo cambiado, un status no declarado), este test
 * falla y obliga a regenerar o corregir el contrato.
 *
 * <p>La validacion la hace swagger-request-validator sobre MockMvc, asi que corre en el job rapido
 * de CI sin necesitar el stack. La auth la aporta el postprocessor {@code jwt()}; se ignora la
 * regla de "falta cabecera de seguridad" porque MockMvc no manda un Authorization real.
 */
@WebMvcTest({ProductController.class, CategoryController.class, ReportController.class})
@Import(SecurityConfig.class)
class OpenApiContractTest {

  // Ruta relativa al modulo backend/, que es el cwd de surefire. El contrato vive en docs/api/.
  private static final String SPEC = "../docs/api/openapi.yaml";

  // Se ignora la validacion de "falta la cabecera de seguridad": MockMvc no manda un header
  // Authorization real (la auth la pone jwt()), y si se mandara, el filtro bearer intentaria
  // decodificarlo con el JwtDecoder mockeado. Lo que se valida es que la RESPUESTA cumple el
  // contrato; el resto de reglas del OpenAPI siguen activas.
  private static final OpenApiInteractionValidator VALIDATOR =
      OpenApiInteractionValidator.createForSpecificationUrl(SPEC)
          .withLevelResolver(
              LevelResolver.create()
                  .withLevel("validation.request.security.missing", ValidationReport.Level.IGNORE)
                  .build())
          .build();

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean ProductService productService;
  @MockBean CategoryService categoryService;
  @MockBean ReportService reportService;

  private static ProductResponse sampleProduct() {
    return new ProductResponse(
        1L,
        "SKU-001",
        "Laptop",
        "A laptop",
        new BigDecimal("999.99"),
        10,
        2,
        true,
        1L,
        "Electronics",
        Instant.now(),
        Instant.now());
  }

  private static CategoryResponse sampleCategory() {
    return new CategoryResponse(
        1L, "Electronics", "Electronic products", Instant.now(), Instant.now());
  }

  @Test
  void getProductById_matchesContract() throws Exception {
    when(productService.findById(1L)).thenReturn(sampleProduct());

    mockMvc
        .perform(
            get("/products/{id}", 1L)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(VALIDATOR));
  }

  @Test
  void listCategories_matchesContract() throws Exception {
    when(categoryService.findAll()).thenReturn(List.of(sampleCategory()));

    mockMvc
        .perform(
            get("/categories")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(VALIDATOR));
  }

  @Test
  void getCategoryById_matchesContract() throws Exception {
    when(categoryService.findById(1L)).thenReturn(sampleCategory());

    mockMvc
        .perform(
            get("/categories/{id}", 1L)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(VALIDATOR));
  }

  @Test
  void stockSummary_matchesContract() throws Exception {
    when(reportService.stockSummary())
        .thenReturn(new StockSummaryResponse(120, 115, 8, new BigDecimal("250000.00"), List.of()));

    mockMvc
        .perform(
            get("/api/reports/stock-summary")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_report:view"))))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid(VALIDATOR));
  }
}
