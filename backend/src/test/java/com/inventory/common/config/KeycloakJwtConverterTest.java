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

  // Verifica que un rol reconocido conserva los scopes OIDC estándar junto a los de negocio.
  @Test
  void recognizedRole_keepsBaseOidcScopes() {
    Jwt jwt = buildJwt(List.of("viewer"), "openid profile email product:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_viewer", "SCOPE_openid", "SCOPE_profile", "SCOPE_email", "SCOPE_product:view");
  }

  // G-5: un token sin ningún rol de realm no recibe scope alguno, ni siquiera los OIDC.
  // Denegar por defecto: si no se reconoce el rol, no se concede nada.
  @Test
  void noRealmRoles_grantsNoScopes() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("scope", "openid profile email product:view")
            .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities()).isEmpty();
  }

  // G-5: un rol desconocido se refleja como ROLE_ pero no habilita ningún scope.
  @Test
  void unrecognizedRole_grantsRoleButNoScopes() {
    Jwt jwt = buildJwt(List.of("manager"), "openid profile product:manage");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_manager");
  }

  // G-4: con varios roles se concede la UNIÓN de sus scopes, no los del primero que coincida.
  // Un warehouse-clerk que además sea auditor debe conservar audit:view y stock:manage.
  @Test
  void multipleRoles_grantUnionOfScopes() {
    Jwt jwt =
        buildJwt(
            List.of("warehouse-clerk", "auditor"),
            "openid product:view product:manage stock:manage audit:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("SCOPE_audit:view", "SCOPE_stock:manage", "SCOPE_product:manage");
  }

  // G-6: un viewer que solicite scopes de escritura a Keycloak no los obtiene como autoridades.
  // Keycloak los emite sin comprobar el rol; el techo por rol es lo que los descarta.
  @Test
  void viewerRequestingWriteScopes_getsOnlyReadScopes() {
    Jwt jwt =
        buildJwt(
            List.of("viewer"),
            "openid product:view product:manage stock:manage user:manage audit:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_viewer", "SCOPE_openid", "SCOPE_product:view");
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .doesNotContain(
            "SCOPE_product:manage", "SCOPE_stock:manage", "SCOPE_user:manage", "SCOPE_audit:view");
  }

  // Verifica que el administrador conserva todos los scopes de negocio que solicite.
  @Test
  void adminRole_grantsAllBusinessScopes() {
    Jwt jwt =
        buildJwt(
            List.of("inventory-admin"),
            "product:view product:manage stock:view stock:manage report:view user:manage"
                + " audit:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains(
            "SCOPE_product:view",
            "SCOPE_product:manage",
            "SCOPE_stock:view",
            "SCOPE_stock:manage",
            "SCOPE_report:view",
            "SCOPE_user:manage",
            "SCOPE_audit:view");
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

  private Jwt buildJwt(List<String> roles, String scope) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-123")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", Map.of("roles", roles))
        .claim("scope", scope)
        .build();
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
