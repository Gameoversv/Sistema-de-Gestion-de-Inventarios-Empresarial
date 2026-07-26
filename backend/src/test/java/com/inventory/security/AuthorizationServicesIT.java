package com.inventory.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * G-1 — Keycloak Authorization Services: Resources, Policies y Permissions.
 *
 * <p>El enunciado nombra <em>Policies</em> de forma explícita dentro del modelo de seguridad
 * granular exigido. Este test verifica que el modelo está declarado en el realm y, sobre esa base,
 * que <strong>decide</strong>: no basta con que las policies existan, tienen que permitir y denegar
 * lo que dice la matriz de permisos.
 *
 * <p>Nota de redacción: la regla S1135 de Sonar busca marcadores de tarea pendiente sin distinguir
 * mayúsculas, y hay un cuantificador español de cuatro letras que coincide con uno de ellos. Ojo al
 * usarlo en comentarios; ya costó un falso positivo en Q-5 y otro aquí.
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

  // Resueltos una vez: el token de admin y el UUID del cliente no cambian entre casos, y
  // la matriz parametrizada de abajo son 28 evaluaciones.
  private static String adminToken;
  private static String clientUuid;

  @BeforeAll
  static void resolveFixtures() {
    adminToken =
        given()
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

    clientUuid =
        given()
            .auth()
            .oauth2(adminToken)
            .queryParam("clientId", CLIENT_ID)
            .when()
            .get(admin("/clients"))
            .then()
            .statusCode(200)
            .extract()
            .path("[0].id");
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private static String admin(String path) {
    return keycloak.getAuthServerUrl() + "/admin/realms/" + REALM + path;
  }

  private static String userId(String username) {
    return given()
        .auth()
        .oauth2(adminToken)
        .queryParam("username", username)
        .queryParam("exact", true)
        .when()
        .get(admin("/users"))
        .then()
        .statusCode(200)
        .extract()
        .path("[0].id");
  }

  private static List<String> names(String collection) {
    return given()
        .auth()
        .oauth2(adminToken)
        .queryParam("max", 100)
        .when()
        .get(admin("/clients/" + clientUuid + "/authz/resource-server/" + collection))
        .then()
        .statusCode(200)
        .extract()
        .path("name");
  }

  /**
   * Pide al motor de políticas una decisión sobre {@code resource#scope} para un usuario concreto.
   * Devuelve PERMIT o DENY.
   */
  private static String decisionFor(String username, String resource, String scope) {
    String body =
        "{\"userId\":\""
            + userId(username)
            + "\",\"entitlements\":false,\"context\":{\"attributes\":{}},"
            + "\"resources\":[{\"name\":\""
            + resource
            + "\",\"scopes\":[{\"name\":\""
            + scope
            + "\"}]}]}";

    return given()
        .auth()
        .oauth2(adminToken)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post(admin("/clients/" + clientUuid + "/authz/resource-server/policy/evaluate"))
        .then()
        .statusCode(200)
        .extract()
        .path("status");
  }

  // ── El modelo está declarado ───────────────────────────────────────────────

  @Test
  @DisplayName("el realm declara los 5 Resources del dominio")
  void resourcesAreDeclared() {
    // `contains` y no exactitud: si Keycloak añadiera su Default Resource, el modelo
    // propio seguiría siendo correcto. Hoy no lo hace — declarar `resources` en el import
    // suprime los que crea por defecto—, pero eso es detalle de la versión, no contrato.
    assertThat(names("resource")).contains("Product", "Stock", "Report", "Audit", "User");
  }

  @Test
  @DisplayName("el realm declara los 7 authorization scopes de la matriz de permisos")
  void scopesAreDeclared() {
    assertThat(names("scope"))
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
    assertThat(names("policy/role"))
        .containsExactlyInAnyOrder(
            "Es Administrador de inventario",
            "Es Encargado de almacen",
            "Es Auditor",
            "Es Consultor");
    assertThat(names("permission/scope"))
        .hasSize(7)
        .contains("Permite product:manage", "Permite user:manage");
  }

  // ── El modelo decide ───────────────────────────────────────────────────────

  /**
   * La matriz entera: 4 roles × 7 permisos = 28 decisiones, espejo de {@code assign_scope_roles} en
   * {@code scripts/keycloak/init-users.sh} y de la tabla del README.
   *
   * <p>Se enumeran las 28 y no una muestra a propósito. Un modelo de autorización se rompe por el
   * cruce que nadie miró, y aquí un {@code applyPolicies} mal editado deja de ser una afirmación de
   * la documentación para ser un test en rojo con nombre y fila.
   */
  @ParameterizedTest(name = "{0} → {1}#{2} = {3}")
  @CsvSource({
    // inventory-admin: los siete
    "it-admin,    Product, product:view,   PERMIT",
    "it-admin,    Product, product:manage, PERMIT",
    "it-admin,    Stock,   stock:view,     PERMIT",
    "it-admin,    Stock,   stock:manage,   PERMIT",
    "it-admin,    Report,  report:view,    PERMIT",
    "it-admin,    Audit,   audit:view,     PERMIT",
    "it-admin,    User,    user:manage,    PERMIT",
    // warehouse-clerk: gestiona catálogo y stock, sin auditoría ni usuarios
    "it-clerk,    Product, product:view,   PERMIT",
    "it-clerk,    Product, product:manage, PERMIT",
    "it-clerk,    Stock,   stock:view,     PERMIT",
    "it-clerk,    Stock,   stock:manage,   PERMIT",
    "it-clerk,    Report,  report:view,    PERMIT",
    "it-clerk,    Audit,   audit:view,     DENY",
    "it-clerk,    User,    user:manage,    DENY",
    // auditor: lectura amplia más la pista de auditoría, cero escritura
    "it-auditor,  Product, product:view,   PERMIT",
    "it-auditor,  Product, product:manage, DENY",
    "it-auditor,  Stock,   stock:view,     PERMIT",
    "it-auditor,  Stock,   stock:manage,   DENY",
    "it-auditor,  Report,  report:view,    PERMIT",
    "it-auditor,  Audit,   audit:view,     PERMIT",
    "it-auditor,  User,    user:manage,    DENY",
    // viewer: solo lectura, y sin auditoría
    "it-viewer,   Product, product:view,   PERMIT",
    "it-viewer,   Product, product:manage, DENY",
    "it-viewer,   Stock,   stock:view,     PERMIT",
    "it-viewer,   Stock,   stock:manage,   DENY",
    "it-viewer,   Report,  report:view,    PERMIT",
    "it-viewer,   Audit,   audit:view,     DENY",
    "it-viewer,   User,    user:manage,    DENY",
  })
  void policyEngineDecidesTheWholeMatrix(
      String username, String resource, String scope, String expected) {
    assertThat(decisionFor(username, resource, scope)).isEqualTo(expected);
  }
}
