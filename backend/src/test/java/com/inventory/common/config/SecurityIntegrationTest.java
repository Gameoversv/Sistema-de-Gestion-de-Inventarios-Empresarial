package com.inventory.common.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.web.PingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
class SecurityIntegrationTest {

  @Autowired MockMvc mockMvc;

  // Prevents Spring from contacting Keycloak at startup to fetch JWKS
  @MockBean JwtDecoder jwtDecoder;

  @Test
  void anonymous_returns401() throws Exception {
    mockMvc.perform(get("/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void withJwt_returns200() throws Exception {
    mockMvc
        .perform(get("/ping").with(jwt().jwt(j -> j.subject("user-abc"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.subject").value("user-abc"));
  }

  @Test
  void withAdminRole_returns200() throws Exception {
    mockMvc
        .perform(
            get("/ping")
                .with(
                    jwt()
                        .jwt(j -> j.subject("admin-xyz"))
                        .authorities(new SimpleGrantedAuthority("ROLE_inventory-admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorities").value("[ROLE_inventory-admin]"));
  }
}
