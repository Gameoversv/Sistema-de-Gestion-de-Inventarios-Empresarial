package com.inventory.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * G-1 — Keycloak Authorization Services: Resources, Policies y Permissions.
 *
 * <p>El enunciado nombra <em>Policies</em> de forma explícita dentro del modelo de seguridad
 * granular exigido. Este test verifica que el modelo está declarado en el realm y, sobre todo, que
 * <strong>decide</strong>: no basta con que las policies existan, tienen que permitir y denegar lo
 * que dice la matriz de permisos.
 *
 * <p>Las decisiones se piden al endpoint de evaluación de políticas de la Admin API, que es el
 * mismo motor que resolvería un RPT en runtime. Que el <em>enforcement</em> en la ruta de petición
 * sea o no la siguiente fase no cambia lo que aquí se comprueba: el modelo es real y evaluable, no
 * configuración decorativa (regla 3 del plan de ejecución).
 *
 * <p>No levanta el contexto de Spring ni Postgres: solo habla con Keycloak, así que corre en
 * segundos frente a los ~35 s de {@link KeycloakAuthIT}.
 */
@Testcontainers
class AuthorizationServicesIT {

  private static final String REALM = "inventory";
  private static final String CLIENT_ID = "inventory-backend";

  @Container
  static final KeycloakContainer keycloak =
      new KeycloakContainer()
          .withRealmImportFile("keycloak/test-realm.json")
          .withStartupTimeout(Duration.ofMinutes(3));

  // ── Helpers ────────────────────────────────────────────────────────────────

  private String adminToken() {
    return given()
        .contentType(ContentType.URLENC)
        .formParam("client_id", "admin-cli")
        .formParam("grant_type", "password")
        .formParam("username", keycloak.getAdminUsername())
        .formParam("password", keycloak.getAdminPassword())
        .when()
        .post(keycloak.getAuthServerUrl() + "/realms/master/protocol/openid-connect/token")
        .then()
        .statusCode(200)
        .extract()
        .path("access_token");
  }

  private String admin(String path) {
    return keycloak.getAuthServerUrl() + "/admin/realms/" + REALM + path;
  }

  /** UUID interno del cliente que hospeda el authorization server. */
  private String clientUuid(String token) {
    return given()
        .auth()
        .oauth2(token)
        .queryParam("clientId", CLIENT_ID)
        .when()
        .get(admin("/clients"))
        .then()
        .statusCode(200)
        .extract()
        .path("[0].id");
  }

  private String userId(String token, String username) {
    return given()
        .auth()
        .oauth2(token)
        .queryParam("username", username)
        .queryParam("exact", true)
        .when()
        .get(admin("/users"))
        .then()
        .statusCode(200)
        .extract()
        .path("[0].id");
  }

  private List<String> names(String token, String uuid, String collection) {
    return given()
        .auth()
        .oauth2(token)
        .queryParam("max", 100)
        .when()
        .get(admin("/clients/" + uuid + "/authz/resource-server/" + collection))
        .then()
        .statusCode(200)
        .extract()
        .path("name");
  }

  /**
   * Pide al motor de políticas una decisión sobre {@code resource#scope} para un usuario concreto.
   * Devuelve PERMIT o DENY.
   */
  private String decisionFor(
      String token, String uuid, String username, String resource, String scope) {
    String body =
        "{\"userId\":\""
            + userId(token, username)
            + "\","
            + "\"entitlements\":false,"
            + "\"context\":{\"attributes\":{}},"
            + "\"resources\":[{\"name\":\""
            + resource
            + "\","
            + "\"scopes\":[{\"name\":\""
            + scope
            + "\"}]}]}";

    return given()
        .auth()
        .oauth2(token)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(admin("/clients/" + uuid + "/authz/resource-server/policy/evaluate"))
        .then()
        .statusCode(200)
        .extract()
        .path("status");
  }

  // ── El modelo está declarado ───────────────────────────────────────────────

  @Test
  @DisplayName("el realm declara los 5 Resources del dominio")
  void resourcesAreDeclared() {
    String token = adminToken();

    List<String> resources = names(token, clientUuid(token), "resource");

    assertThat(resources).contains("Product", "Stock", "Report", "Audit", "User");
  }

  @Test
  @DisplayName("el realm declara los 7 authorization scopes de la matriz de permisos")
  void scopesAreDeclared() {
    String token = adminToken();

    List<String> scopes = names(token, clientUuid(token), "scope");

    assertThat(scopes)
        .containsExactlyInAnyOrder(
            "product:view",
            "product:manage",
            "stock:view",
            "stock:manage",
            "report:view",
            "audit:view",
            "user:manage");
  }

  @Test
  @DisplayName("el realm declara una Policy por rol y un Permission por scope")
  void policiesAndPermissionsAreDeclared() {
    String token = adminToken();
    String uuid = clientUuid(token);

    List<String> policies = names(token, uuid, "policy/role");
    List<String> permissions = names(token, uuid, "permission/scope");

    assertThat(policies)
        .containsExactlyInAnyOrder(
            "Es Administrador de inventario",
            "Es Encargado de almacen",
            "Es Auditor",
            "Es Consultor");
    assertThat(permissions).hasSize(7).contains("Permite product:manage", "Permite user:manage");
  }

  // ── El modelo decide ───────────────────────────────────────────────────────
  // Lo que separa un modelo real de uno decorativo: que evaluarlo devuelva PERMIT y DENY
  // donde corresponde. Si estos tests pasaran solos los de arriba, el modelo podría estar
  // declarado y no gobernar nada.

  @Test
  @DisplayName("PERMIT: el administrador puede gestionar productos")
  void adminIsPermittedToManageProducts() {
    String token = adminToken();

    String decision =
        decisionFor(token, clientUuid(token), "it-admin", "Product", "product:manage");

    assertThat(decision).isEqualTo("PERMIT");
  }

  @Test
  @DisplayName("DENY: el consultor no puede gestionar productos")
  void viewerIsDeniedProductManagement() {
    String token = adminToken();

    String decision =
        decisionFor(token, clientUuid(token), "it-viewer", "Product", "product:manage");

    assertThat(decision).isEqualTo("DENY");
  }

  @Test
  @DisplayName("PERMIT: el consultor sí puede ver productos")
  void viewerIsPermittedToViewProducts() {
    String token = adminToken();

    String decision = decisionFor(token, clientUuid(token), "it-viewer", "Product", "product:view");

    assertThat(decision).isEqualTo("PERMIT");
  }

  @Test
  @DisplayName("DENY: user:manage está reservado al administrador")
  void viewerIsDeniedUserManagement() {
    String token = adminToken();

    String decision = decisionFor(token, clientUuid(token), "it-viewer", "User", "user:manage");

    assertThat(decision).isEqualTo("DENY");
  }
}
