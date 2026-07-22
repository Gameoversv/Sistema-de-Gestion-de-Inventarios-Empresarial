package com.inventory.product.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.common.config.SecurityConfig;
import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import com.inventory.product.service.ProductService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean ProductService productService;

  private ProductResponse sampleProduct() {
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

  // ── GET /products ─────────────────────────────────────────────────────────

  // Verifica que listar productos sin autenticación retorna 401.
  @Test
  void list_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/products")).andExpect(status().isUnauthorized());
  }

  // Verifica que con scope product:view se obtiene 200 con la lista paginada de productos.
  @Test
  void list_withProductViewScope_returns200WithContent() throws Exception {
    when(productService.findAll(isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(sampleProduct())));

    mockMvc
        .perform(
            get("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].sku").value("SKU-001"))
        .andExpect(jsonPath("$.content[0].stock").value(10));
  }

  // Verifica que con scope product:manage también se obtiene 200 al listar productos.
  @Test
  void list_withProductManageScope_returns200() throws Exception {
    when(productService.findAll(isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isOk());
  }

  // Verifica que un scope insuficiente (audit:view) retorna 403 al listar productos.
  @Test
  void list_withInsufficientScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que el parámetro search se propaga correctamente al servicio.
  @Test
  void list_withSearchFilter_passesFilterToService() throws Exception {
    when(productService.findAll(eq("laptop"), isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(sampleProduct())));

    mockMvc
        .perform(
            get("/products")
                .param("search", "laptop")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk());

    verify(productService).findAll(eq("laptop"), isNull(), isNull(), any());
  }

  // Verifica que los parámetros categoryId y active se propagan correctamente al servicio.
  @Test
  void list_withCategoryAndActiveFilters_passesFiltersToService() throws Exception {
    when(productService.findAll(isNull(), eq(5L), eq(true), any()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/products")
                .param("categoryId", "5")
                .param("active", "true")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk());

    verify(productService).findAll(isNull(), eq(5L), eq(true), any());
  }

  // ── GET /products/{id} ────────────────────────────────────────────────────

  // Verifica que obtener un producto existente por ID retorna 200 con los datos correctos.
  @Test
  void getById_existingProduct_returns200() throws Exception {
    when(productService.findById(1L)).thenReturn(sampleProduct());

    mockMvc
        .perform(
            get("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.sku").value("SKU-001"))
        .andExpect(jsonPath("$.name").value("Laptop"));
  }

  // Verifica que buscar un producto inexistente por ID retorna 404.
  @Test
  void getById_missingProduct_returns404() throws Exception {
    when(productService.findById(99L))
        .thenThrow(new ResourceNotFoundException("Product not found: 99"));

    mockMvc
        .perform(
            get("/products/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isNotFound());
  }

  // Verifica que buscar un producto por ID sin autenticación retorna 401.
  @Test
  void getById_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/products/1")).andExpect(status().isUnauthorized());
  }

  // ── POST /products ────────────────────────────────────────────────────────

  // Verifica que crear un producto sin autenticación retorna 401.
  @Test
  void create_anonymous_returns401() throws Exception {
    mockMvc.perform(post("/products")).andExpect(status().isUnauthorized());
  }

  // Verifica que con solo scope product:view no se puede crear un producto (retorna 403).
  @Test
  void create_viewScopeOnly_returns403() throws Exception {
    var request =
        new ProductCreateRequest(
            "SKU-NEW", "New Product", null, new BigDecimal("50.00"), 5, 1, true, null);

    mockMvc
        .perform(
            post("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope product:manage y datos válidos se crea el producto con 201.
  @Test
  void create_validRequest_returns201() throws Exception {
    var request =
        new ProductCreateRequest(
            "SKU-NEW", "New Product", "desc", new BigDecimal("50.00"), 5, 1, true, null);
    when(productService.create(any())).thenReturn(sampleProduct());

    mockMvc
        .perform(
            post("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("SKU-001"));
  }

  // Verifica que crear un producto sin campos requeridos retorna 400.
  @Test
  void create_missingRequiredFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // Verifica que crear un producto con SKU duplicado retorna 409.
  @Test
  void create_duplicateSku_returns409() throws Exception {
    var request =
        new ProductCreateRequest(
            "SKU-DUP", "Product", "desc", new BigDecimal("10.00"), 1, 0, true, null);
    when(productService.create(any()))
        .thenThrow(new ConflictException("SKU already exists: SKU-DUP"));

    mockMvc
        .perform(
            post("/products")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  // ── PUT /products/{id} ────────────────────────────────────────────────────

  // Verifica que actualizar un producto con datos válidos retorna 200.
  @Test
  void update_validRequest_returns200() throws Exception {
    var request =
        new ProductUpdateRequest(
            "SKU-001", "Updated Laptop", "desc", new BigDecimal("1199.99"), 8, 2, true, null);
    when(productService.update(eq(1L), any())).thenReturn(sampleProduct());

    mockMvc
        .perform(
            put("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("SKU-001"));
  }

  // Verifica que actualizar un producto inexistente retorna 404.
  @Test
  void update_notFound_returns404() throws Exception {
    var request =
        new ProductUpdateRequest(
            "SKU-001", "Laptop", "desc", new BigDecimal("999.99"), 10, 2, true, null);
    when(productService.update(eq(99L), any()))
        .thenThrow(new ResourceNotFoundException("Product not found: 99"));

    mockMvc
        .perform(
            put("/products/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  // Verifica que actualizar un producto con body inválido retorna 400.
  @Test
  void update_invalidRequest_returns400() throws Exception {
    mockMvc
        .perform(
            put("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // ── PATCH /products/{id} ──────────────────────────────────────────────────

  // Verifica que patch de un producto con datos válidos retorna 200.
  @Test
  void patch_validRequest_returns200() throws Exception {
    var request =
        new ProductPatchRequest(null, "Patched Laptop", null, null, null, null, null, null);
    when(productService.patch(eq(1L), any())).thenReturn(sampleProduct());

    mockMvc
        .perform(
            patch("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("SKU-001"));
  }

  // Verifica que patch de un producto inexistente retorna 404.
  @Test
  void patch_notFound_returns404() throws Exception {
    var request = new ProductPatchRequest(null, "Name", null, null, null, null, null, null);
    when(productService.patch(eq(99L), any()))
        .thenThrow(new ResourceNotFoundException("Product not found: 99"));

    mockMvc
        .perform(
            patch("/products/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  // Verifica que patch sin autenticación retorna 401.
  @Test
  void patch_anonymous_returns401() throws Exception {
    mockMvc.perform(patch("/products/1")).andExpect(status().isUnauthorized());
  }

  // ── DELETE /products/{id} ─────────────────────────────────────────────────

  // Verifica que eliminar un producto existente retorna 204 y delega al servicio.
  @Test
  void delete_existingProduct_returns204() throws Exception {
    mockMvc
        .perform(
            delete("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isNoContent());

    verify(productService).delete(1L);
  }

  // Verifica que eliminar un producto sin autenticación retorna 401.
  @Test
  void delete_anonymous_returns401() throws Exception {
    mockMvc.perform(delete("/products/1")).andExpect(status().isUnauthorized());
  }

  // Verifica que con solo scope product:view no se puede eliminar un producto (retorna 403).
  @Test
  void delete_viewScopeOnly_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/products/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que eliminar un producto inexistente retorna 404.
  @Test
  void delete_notFound_returns404() throws Exception {
    doThrow(new ResourceNotFoundException("Product not found: 99"))
        .when(productService)
        .delete(99L);

    mockMvc
        .perform(
            delete("/products/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isNotFound());
  }
}
