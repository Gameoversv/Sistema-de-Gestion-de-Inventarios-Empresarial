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

  // Los scopes OIDC estándar del token se reflejan como autoridades junto a los de negocio.
  // Ninguno protege un endpoint por sí solo; se conservan porque es el comportamiento estándar
  // de un resource server y porque MeController los expone al frontend.
  @Test
  void tokenScopes_mappedWithScopePrefix() {
    Jwt jwt = buildJwt(List.of("viewer"), "openid profile email product:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_viewer", "SCOPE_openid", "SCOPE_profile", "SCOPE_email", "SCOPE_product:view");
  }

  // G-2: el backend confía en el claim scope y no lo reintersecta contra una tabla local.
  // Que un viewer no llegue a tener product:manage EN EL TOKEN es responsabilidad de los
  // scope-mappings del realm, y lo verifica KeycloakAuthIT contra un Keycloak real. Aquí se
  // fija la otra mitad del contrato: lo que Keycloak emite, el backend lo respeta tal cual.
  @Test
  void scopeClaim_isTrustedVerbatim() {
    Jwt jwt = buildJwt(List.of("warehouse-clerk"), "openid product:view product:manage");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_warehouse-clerk", "SCOPE_openid", "SCOPE_product:view", "SCOPE_product:manage");
  }

  // Un token sin roles de realm solo aporta sus scopes. Keycloak no emite scopes de negocio a
  // quien no tiene rol —los scope-mappings los atan a un rol—, así que en la práctica un token
  // así llega solo con los OIDC, que no autorizan nada.
  @Test
  void noRealmRoles_grantsOnlyTokenScopes() {
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

  // Un rol que el backend no conoce ya no cambia nada: los roles no autorizan, autorizan los
  // scopes. El enunciado prohíbe validar acceso por nombre de rol y esto lo hace estructural.
  @Test
  void unknownRole_doesNotAffectScopes() {
    Jwt jwt = buildJwt(List.of("manager"), "openid product:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_manager", "SCOPE_openid", "SCOPE_product:view");
  }

  // Un usuario con varios roles recibe el token que Keycloak componga para la unión de sus
  // scope-mappings. El backend no recalcula esa unión: la lee.
  @Test
  void multipleRoles_scopesComeFromToken() {
    Jwt jwt =
        buildJwt(
            List.of("warehouse-clerk", "auditor"),
            "openid product:view product:manage stock:manage audit:view");

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains(
            "ROLE_warehouse-clerk",
            "ROLE_auditor",
            "SCOPE_audit:view",
            "SCOPE_stock:manage",
            "SCOPE_product:manage");
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
