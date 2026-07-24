package com.inventory.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TEST-1 — Integration Testing con Keycloak real, exigido por el enunciado ("Testcontainers: Base
 * de datos real, Keycloak, Integraciones").
 *
 * <p>A diferencia de {@code SecurityIntegrationTest}, que mockea el {@code JwtDecoder}, aquí el
 * backend valida tokens firmados de verdad por un Keycloak levantado en contenedor. Se ejercita la
 * cadena completa: Keycloak emite el token → el resource server descarga su JWKS y valida firma,
 * emisor y expiración → intersecta scopes con el rol → {@code @PreAuthorize} decide.
 *
 * <p>El último test reverifica G-8 a nivel de integración: los scope-mappings del realm impiden que
 * un viewer obtenga scopes elevados en el token emitido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class KeycloakAuthIT {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("inventory_test")
          .withUsername("test")
          .withPassword("test");

  // Sin tag explícito: se usa la imagen por defecto de testcontainers-keycloak, cuya wait
  // strategy (health en el puerto de management) está emparejada con esa versión. Forzar
  // keycloak:24.0 dejaba la sonda /health/started sin 200 y el contenedor no arrancaba.
  @Container
  static final KeycloakContainer keycloak =
      new KeycloakContainer()
          .withRealmImportFile("keycloak/test-realm.json")
          .withStartupTimeout(Duration.ofMinutes(3));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    String issuer = keycloak.getAuthServerUrl() + "/realms/inventory";
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> issuer + "/protocol/openid-connect/certs");
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @LocalServerPort int port;

  @Autowired ObjectMapper objectMapper;

  private static final String PRODUCT_BODY =
      "{\"name\":\"IT"
          + " product\",\"sku\":\"IT-SEC-1\",\"price\":1.00,\"stock\":1,\"minimumStock\":0}";

  // ── Helpers ────────────────────────────────────────────────────────────────

  private String token(String username, String password, String scope) {
    return given()
        .contentType(ContentType.URLENC)
        .formParam("client_id", "inventory-frontend")
        .formParam("grant_type", "password")
        .formParam("username", username)
        .formParam("password", password)
        .formParam("scope", scope)
        .when()
        .post(keycloak.getAuthServerUrl() + "/realms/inventory/protocol/openid-connect/token")
        .then()
        .statusCode(200)
        .extract()
        .path("access_token");
  }

  private String scopeClaim(String jwt) throws Exception {
    String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    JsonNode node = objectMapper.readTree(payload);
    return node.path("scope").asText("");
  }

  private String payload(String jwt) {
    return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("token real de admin: lista productos (200)")
  void adminToken_canListProducts() {
    String token = token("it-admin", "it-admin-pass", "openid product:view product:manage");
    System.out.println("=== TEST-1 DIAG admin payload === " + payload(token));

    given()
        .baseUri("http://localhost:" + port)
        .auth()
        .oauth2(token)
        .when()
        .get("/products")
        .then()
        .statusCode(200);
  }

  @Test
  @DisplayName("token real de viewer: no puede crear productos (403)")
  void viewerToken_cannotCreateProduct() {
    String token = token("it-viewer", "it-viewer-pass", "openid product:view");

    given()
        .baseUri("http://localhost:" + port)
        .auth()
        .oauth2(token)
        .contentType(ContentType.JSON)
        .body(PRODUCT_BODY)
        .when()
        .post("/products")
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("sin token: 401")
  void noToken_isUnauthorized() {
    given().baseUri("http://localhost:" + port).when().get("/products").then().statusCode(401);
  }

  @Test
  @DisplayName("G-8 (nivel IT): Keycloak no emite scopes elevados a un viewer")
  void keycloakGatesScopeEscalation() throws Exception {
    // El viewer pide product:manage; los scope-mappings del realm deben negarlo.
    String elevated = token("it-viewer", "it-viewer-pass", "openid product:view product:manage");

    String emitted = scopeClaim(elevated);

    assertThat(emitted).contains("product:view");
    assertThat(emitted).doesNotContain("product:manage");
  }
}
