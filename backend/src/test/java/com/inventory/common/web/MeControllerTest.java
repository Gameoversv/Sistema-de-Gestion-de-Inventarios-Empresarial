package com.inventory.common.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;

  @Test
  void anonymous_returns401() throws Exception {
    mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void withJwt_returnsSubject() throws Exception {
    mockMvc
        .perform(get("/me").with(jwt().jwt(j -> j.subject("user-abc"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("user-abc"));
  }

  @Test
  void withRealmRole_roleAppearsInRolesArray() throws Exception {
    mockMvc
        .perform(
            get("/me")
                .with(
                    jwt()
                        .jwt(j -> j.subject("manager-1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_manager"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("manager"))
        .andExpect(jsonPath("$.scopes").isEmpty());
  }

  @Test
  void withScope_scopeAppearsInScopesArray() throws Exception {
    mockMvc
        .perform(
            get("/me")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user-xyz"))
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_openid"),
                            new SimpleGrantedAuthority("SCOPE_profile"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(jsonPath("$.scopes[0]").value("openid"))
        .andExpect(jsonPath("$.scopes[1]").value("profile"));
  }

  @Test
  void withEmailClaim_emailIncludedInResponse() throws Exception {
    mockMvc
        .perform(
            get("/me")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user-1")
                                    .claim("email", "user@example.com")
                                    .claim("preferred_username", "jdoe"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.preferredUsername").value("jdoe"));
  }
}
