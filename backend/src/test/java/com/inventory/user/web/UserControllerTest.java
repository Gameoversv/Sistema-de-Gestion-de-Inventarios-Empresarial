package com.inventory.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.config.SecurityConfig;
import com.inventory.user.dto.UserResponse;
import com.inventory.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El punto de estos tests no es el CRUD, es la <strong>autorización</strong>: {@code user:manage}
 * era el único de los siete permisos de la matriz obligatoria que no protegía nada. Cada operación
 * se comprueba con el permiso y sin él.
 */
@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;
  @MockBean UserService service;

  private static final UserResponse SAMPLE =
      new UserResponse(
          "id-1", "inv_admin", "a@b.c", "Admin", "Test", true, List.of("inventory-admin"));

  private static org.springframework.test.web.servlet.request.RequestPostProcessor withManage() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:manage"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor withoutManage() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_product:view"));
  }

  @Test
  @DisplayName("con user:manage se listan los usuarios con sus roles")
  void list_withPermission_returnsUsers() throws Exception {
    when(service.list(any(), anyInt(), anyInt())).thenReturn(List.of(SAMPLE));

    mockMvc
        .perform(get("/api/users").with(withManage()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("inv_admin"))
        .andExpect(jsonPath("$[0].roles[0]").value("inventory-admin"));
  }

  @Test
  @DisplayName("sin user:manage el listado responde 403")
  void list_withoutPermission_isForbidden() throws Exception {
    mockMvc.perform(get("/api/users").with(withoutManage())).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("sin token el listado responde 401")
  void list_withoutToken_isUnauthorized() throws Exception {
    mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("la creación devuelve 201 con el usuario resultante")
  void create_returnsCreated() throws Exception {
    when(service.create(any())).thenReturn(SAMPLE);

    mockMvc
        .perform(
            post("/api/users")
                .with(withManage())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"nuevo\",\"email\":\"n@b.c\",\"password\":\"12345678\","
                        + "\"roles\":[\"viewer\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("id-1"));
  }

  @Test
  @DisplayName("un alta sin contraseña se rechaza con 400 antes de tocar Keycloak")
  void create_withoutPassword_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .with(withManage())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nuevo\",\"email\":\"n@b.c\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("crear sin user:manage responde 403")
  void create_withoutPermission_isForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/users")
                .with(withoutManage())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"nuevo\",\"email\":\"n@b.c\",\"password\":\"12345678\","
                        + "\"roles\":[]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("el reemplazo de roles delega en el servicio y devuelve el usuario")
  void replaceRoles_returnsUpdatedUser() throws Exception {
    when(service.replaceRoles(anyString(), any())).thenReturn(SAMPLE);

    mockMvc
        .perform(
            put("/api/users/id-1/roles")
                .with(withManage())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"auditor\"]}"))
        .andExpect(status().isOk());

    verify(service).replaceRoles(anyString(), any());
  }

  @Test
  @DisplayName("el borrado responde 204 sin cuerpo")
  void delete_returnsNoContent() throws Exception {
    mockMvc.perform(delete("/api/users/id-1").with(withManage())).andExpect(status().isNoContent());

    verify(service).delete("id-1");
  }

  @Test
  @DisplayName("borrar sin user:manage responde 403")
  void delete_withoutPermission_isForbidden() throws Exception {
    mockMvc
        .perform(delete("/api/users/id-1").with(withoutManage()))
        .andExpect(status().isForbidden());
  }
}
