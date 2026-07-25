package com.inventory.common.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.inventory.common.web.PingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TEST-11 — Verificación del comportamiento CORS en tiempo de ejecución, complementaria a {@link
 * CorsProfilesTest}, que solo comprueba los valores de configuración por perfil. Aquí se ejercita
 * el filtro CORS de verdad: un preflight de un origen permitido recibe la cabecera {@code
 * Access-Control-Allow-Origin}, y uno de un origen no permitido no la recibe.
 *
 * <p>El perfil {@code test} hereda el origen por defecto {@code http://localhost:3000} de {@code
 * application.yml}.
 */
@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class CorsHttpTest {

  private static final String ALLOWED = "http://localhost:3000";
  private static final String DISALLOWED = "http://evil.example.com";

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;

  @Test
  void preflight_allowedOrigin_getsCorsHeader() throws Exception {
    mockMvc
        .perform(
            options("/ping")
                .header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
  }

  @Test
  void preflight_disallowedOrigin_hasNoCorsHeader() throws Exception {
    mockMvc
        .perform(
            options("/ping")
                .header("Origin", DISALLOWED)
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void actualRequest_allowedOrigin_getsCorsHeader() throws Exception {
    mockMvc
        .perform(get("/ping").header("Origin", ALLOWED).with(jwt()))
        .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
  }
}
