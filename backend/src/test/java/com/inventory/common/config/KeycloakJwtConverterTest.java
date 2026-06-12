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

  @Test
  void realmRoles_mappedWithRolePrefix() {
    Jwt jwt = buildJwt(Map.of("roles", List.of("inventory-admin", "viewer")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_inventory-admin", "ROLE_viewer");
  }

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

  @Test
  void emptyRolesList_returnsEmptyAuthorities() {
    Jwt jwt = buildJwt(Map.of("roles", List.of()));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities()).isEmpty();
  }

  private Jwt buildJwt(Map<String, Object> realmAccessContent) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-123")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", realmAccessContent)
        .build();
  }
}
