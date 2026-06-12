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

  @Test
  void list_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
  }

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

  @Test
  void getById_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/categories/1")).andExpect(status().isUnauthorized());
  }

  // ── POST /categories ──────────────────────────────────────────────────────

  @Test
  void create_anonymous_returns401() throws Exception {
    mockMvc.perform(post("/categories")).andExpect(status().isUnauthorized());
  }

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

  @Test
  void update_anonymous_returns401() throws Exception {
    mockMvc.perform(put("/categories/1")).andExpect(status().isUnauthorized());
  }

  // ── DELETE /categories/{id} ───────────────────────────────────────────────

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

  @Test
  void delete_anonymous_returns401() throws Exception {
    mockMvc.perform(delete("/categories/1")).andExpect(status().isUnauthorized());
  }

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
