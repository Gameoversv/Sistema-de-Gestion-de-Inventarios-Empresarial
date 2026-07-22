package com.inventory.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@DisplayName("AuthenticatedUserMdcFilter")
class AuthenticatedUserMdcFilterTest {

  private final AuthenticatedUserMdcFilter filter = new AuthenticatedUserMdcFilter();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  @DisplayName("registra preferred_username cuando el principal es un JWT de Keycloak")
  void putsPreferredUsernameInMdc() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("8f2c0d1e-4b3a-4c7d-9e0f-1a2b3c4d5e6f")
            .claim("preferred_username", "inv_admin")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_viewer"))));

    assertThat(captureUserDuringChain()).isEqualTo("inv_admin");
  }

  @Test
  @DisplayName("cae al subject cuando el token no trae preferred_username")
  void fallsBackToSubject() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "8f2c0d1e-4b3a-4c7d-9e0f-1a2b3c4d5e6f")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

    assertThat(captureUserDuringChain()).isEqualTo("8f2c0d1e-4b3a-4c7d-9e0f-1a2b3c4d5e6f");
  }

  @Test
  @DisplayName("registra anonymous cuando no hay autenticación")
  void putsAnonymousWhenUnauthenticated() throws Exception {
    assertThat(captureUserDuringChain()).isEqualTo("anonymous");
  }

  @Test
  @DisplayName("registra anonymous cuando la autenticación no está autenticada")
  void putsAnonymousWhenAuthenticationNotAuthenticated() throws Exception {
    UsernamePasswordAuthenticationToken unauthenticated =
        new UsernamePasswordAuthenticationToken("inv_viewer", "secret");
    SecurityContextHolder.getContext().setAuthentication(unauthenticated);

    assertThat(captureUserDuringChain()).isEqualTo("anonymous");
  }

  private String captureUserDuringChain() throws Exception {
    String[] captured = new String[1];
    FilterChain chain = (req, res) -> captured[0] = MDC.get(AuthenticatedUserMdcFilter.MDC_USER);

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/products"), new MockHttpServletResponse(), chain);
    return captured[0];
  }
}
