package com.inventory.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtConverterTest {

  private final Converter<Jwt, AbstractAuthenticationToken> converter =
      new SecurityConfig().keycloakJwtConverter();

  // Verifica que los roles de realm_access se convierten a autoridades con prefijo ROLE_.
  @Test
  void realmRoles_mappedWithRolePrefix() {
    Jwt jwt = buildJwtWithRoles(Map.of("roles", List.of("inventory-admin", "viewer")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_inventory-admin", "ROLE_viewer");
  }

  // Verifica que un JWT sin claim realm_access produce una lista de autoridades vacía.
  @Test
  void missingRealmAccess_returnsEmptyAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities()).isEmpty();
  }

  // Verifica que realm_access con lista de roles vacía produce una lista de autoridades vacía.
  @Test
  void emptyRolesList_returnsEmptyAuthorities() {
    Jwt jwt = buildJwtWithRoles(Map.of("roles", List.of()));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities()).isEmpty();
  }

  // Verifica que los scopes del JWT se convierten a autoridades con prefijo SCOPE_.
  @Test
  void scopes_mappedWithScopePrefix() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("scope", "openid profile email")
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile", "SCOPE_email");
  }

  // Verifica que roles y scopes presentes en el JWT se combinan en las autoridades resultantes.
  @Test
  void rolesAndScopes_bothMapped() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("realm_access", Map.of("roles", List.of("manager")))
            .claim("scope", "openid profile")
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_manager", "SCOPE_openid", "SCOPE_profile");
  }

  // Verifica que un scope en blanco no genera ninguna autoridad SCOPE_ en el token.
  @Test
  void blankScope_returnsNoScopeAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("scope", "  ")
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities()).isEmpty();
  }

  private Jwt buildJwtWithRoles(Map<String, Object> realmAccessContent) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-123")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", realmAccessContent)
        .build();
  }
}
