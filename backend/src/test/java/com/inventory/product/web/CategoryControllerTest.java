package com.inventory.product.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.common.config.SecurityConfig;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import com.inventory.product.service.CategoryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean CategoryService categoryService;

  private CategoryResponse sampleCategory() {
    return new CategoryResponse(
        1L, "Electronics", "Electronic products", Instant.now(), Instant.now());
  }

  // ── GET /categories ───────────────────────────────────────────────────────

  // Verifica que listar categorías sin autenticación retorna 401.
  @Test
  void list_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
  }

  // Verifica que con scope product:view se obtiene 200 y la lista de categorías.
  @Test
  void list_withProductViewScope_returns200WithList() throws Exception {
    when(categoryService.findAll()).thenReturn(List.of(sampleCategory()));

    mockMvc
        .perform(
            get("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("Electronics"));
  }

  // Verifica que con scope product:manage se obtiene 200 con lista vacía cuando no hay categorías.
  @Test
  void list_withProductManageScope_returns200EmptyList() throws Exception {
    when(categoryService.findAll()).thenReturn(List.of());

    mockMvc
        .perform(
            get("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  // Verifica que un scope insuficiente (audit:view) retorna 403 al listar categorías.
  @Test
  void list_withInsufficientScope_returns403() throws Exception {
    mockMvc
        .perform(
            get("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("auditor"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_audit:view"))))
        .andExpect(status().isForbidden());
  }

  // ── GET /categories/{id} ─────────────────────────────────────────────────

  // Verifica que obtener una categoría existente por ID retorna 200 con los datos correctos.
  @Test
  void getById_existingCategory_returns200() throws Exception {
    when(categoryService.findById(1L)).thenReturn(sampleCategory());

    mockMvc
        .perform(
            get("/categories/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Electronics"));
  }

  // Verifica que buscar una categoría inexistente retorna 404.
  @Test
  void getById_missingCategory_returns404() throws Exception {
    when(categoryService.findById(99L))
        .thenThrow(new ResourceNotFoundException("Category not found: 99"));

    mockMvc
        .perform(
            get("/categories/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isNotFound());
  }

  // Verifica que buscar una categoría por ID sin autenticación retorna 401.
  @Test
  void getById_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/categories/1")).andExpect(status().isUnauthorized());
  }

  // ── POST /categories ──────────────────────────────────────────────────────

  // Verifica que crear una categoría sin autenticación retorna 401.
  @Test
  void create_anonymous_returns401() throws Exception {
    mockMvc.perform(post("/categories")).andExpect(status().isUnauthorized());
  }

  // Verifica que con solo scope product:view no se puede crear una categoría (retorna 403).
  @Test
  void create_viewScopeOnly_returns403() throws Exception {
    var request = new CategoryCreateRequest("Electronics", null);

    mockMvc
        .perform(
            post("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  // Verifica que con scope product:manage y datos válidos se crea la categoría con 201.
  @Test
  void create_validRequest_returns201() throws Exception {
    var request = new CategoryCreateRequest("Electronics", "Electronic products");
    when(categoryService.create(any())).thenReturn(sampleCategory());

    mockMvc
        .perform(
            post("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Electronics"));
  }

  // Verifica que crear una categoría con nombre en blanco retorna 400.
  @Test
  void create_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest());
  }

  // Verifica que crear una categoría sin campo name retorna 400.
  @Test
  void create_missingName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/categories")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // ── PUT /categories/{id} ─────────────────────────────────────────────────

  // Verifica que actualizar una categoría con datos válidos retorna 200 con la respuesta.
  @Test
  void update_validRequest_returns200() throws Exception {
    var request = new CategoryUpdateRequest("Updated Electronics", "Updated description");
    when(categoryService.update(eq(1L), any())).thenReturn(sampleCategory());

    mockMvc
        .perform(
            put("/categories/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Electronics"));
  }

  // Verifica que actualizar una categoría inexistente retorna 404.
  @Test
  void update_notFound_returns404() throws Exception {
    var request = new CategoryUpdateRequest("Electronics", null);
    when(categoryService.update(eq(99L), any()))
        .thenThrow(new ResourceNotFoundException("Category not found: 99"));

    mockMvc
        .perform(
            put("/categories/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  // Verifica que actualizar una categoría con nombre en blanco retorna 400.
  @Test
  void update_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            put("/categories/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest());
  }

  // Verifica que actualizar una categoría sin autenticación retorna 401.
  @Test
  void update_anonymous_returns401() throws Exception {
    mockMvc.perform(put("/categories/1")).andExpect(status().isUnauthorized());
  }

  // ── DELETE /categories/{id} ───────────────────────────────────────────────

  // Verifica que eliminar una categoría existente retorna 204 y delega al servicio.
  @Test
  void delete_existingCategory_returns204() throws Exception {
    mockMvc
        .perform(
            delete("/categories/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isNoContent());

    verify(categoryService).delete(1L);
  }

  // Verifica que eliminar una categoría sin autenticación retorna 401.
  @Test
  void delete_anonymous_returns401() throws Exception {
    mockMvc.perform(delete("/categories/1")).andExpect(status().isUnauthorized());
  }

  // Verifica que con solo scope product:view no se puede eliminar una categoría (retorna 403).
  @Test
  void delete_viewScopeOnly_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/categories/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("viewer"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:view"))))
        .andExpect(status().isForbidden());
  }

  // Verifica que eliminar una categoría inexistente retorna 404.
  @Test
  void delete_notFound_returns404() throws Exception {
    doThrow(new ResourceNotFoundException("Category not found: 99"))
        .when(categoryService)
        .delete(99L);

    mockMvc
        .perform(
            delete("/categories/99")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_product:manage"))))
        .andExpect(status().isNotFound());
  }
}
