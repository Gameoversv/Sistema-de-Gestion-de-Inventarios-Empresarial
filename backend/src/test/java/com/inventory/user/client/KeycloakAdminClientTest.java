package com.inventory.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.inventory.common.exception.BusinessException;
import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Cubre el adaptador contra la Admin API sin levantar Keycloak: {@link MockRestServiceServer}
 * intercepta las peticiones del {@link RestClient}.
 *
 * <p>Lo que interesa comprobar aquí no es que Spring sepa hacer HTTP, sino las decisiones propias
 * del adaptador: que el token de servicio se cachee, que un 404 se traduzca a {@link
 * ResourceNotFoundException} y no a un error genérico, que un 409 al crear se convierta en {@link
 * ConflictException}, que el identificador del usuario nuevo se saque de la cabecera {@code
 * Location}, y que una lista de roles vacía no genere ninguna llamada.
 */
class KeycloakAdminClientTest {

  private static final String BASE = "http://kc:8080";
  private static final String TOKEN_URI = BASE + "/realms/inventory/protocol/openid-connect/token";
  private static final String USERS_URI = BASE + "/admin/realms/inventory/users";
  private static final String REALM_MAPPINGS = "/role-mappings/realm";
  private static final String UID = "11111111-2222-3333-4444-555555555555";

  private MockRestServiceServer server;
  private KeycloakAdminClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new KeycloakAdminClient(builder, BASE, "inventory", "inventory-backend", "s3cret");
  }

  /** Responde al `client_credentials` inicial. Casi todos los casos lo necesitan primero. */
  private void expectTokenRequest() {
    server
        .expect(ExpectedCount.once(), requestTo(TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"svc-token\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("sin secreto configurado no se intenta siquiera pedir el token")
  void missingSecret_failsFast() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();
    var unconfigured = new KeycloakAdminClient(builder, BASE, "inventory", "inventory-backend", "");

    assertThatThrownBy(() -> unconfigured.realmRoles())
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("KC_BACKEND_CLIENT_SECRET");

    strict.verify();
  }

  @Test
  @DisplayName("el token de servicio se pide una vez y se reutiliza")
  void serviceToken_isCachedBetweenCalls() {
    expectTokenRequest();
    server
        .expect(ExpectedCount.twice(), requestTo(BASE + "/admin/realms/inventory/roles"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    client.realmRoles();
    client.realmRoles();

    // Si el token no se cacheara, habria dos peticiones al endpoint de token y verify fallaria.
    server.verify();
  }

  @Test
  @DisplayName("el listado añade el término de búsqueda solo cuando viene informado")
  void listUsers_appendsSearchWhenPresent() {
    expectTokenRequest();
    server
        .expect(requestTo(USERS_URI + "?first=0&max=20&search=ana"))
        .andRespond(withSuccess("[{\"id\":\"u1\"}]", MediaType.APPLICATION_JSON));

    assertThat(client.listUsers("ana", 0, 20)).hasSize(1);
    server.verify();
  }

  @Test
  @DisplayName("un usuario inexistente se traduce a ResourceNotFoundException")
  void findUser_notFound_throwsResourceNotFound() {
    expectTokenRequest();
    server.expect(requestTo(USERS_URI + "/" + UID)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.findUser(UID))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(UID);
  }

  @Test
  @DisplayName("el alta devuelve el identificador extraído de la cabecera Location")
  void createUser_returnsIdFromLocation() {
    expectTokenRequest();
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.LOCATION, "http://kc:8080/admin/realms/inventory/users/nuevo-id");
    server
        .expect(requestTo(USERS_URI))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CREATED).headers(headers));

    assertThat(client.createUser(Map.of("username", "ana"))).isEqualTo("nuevo-id");
  }

  @Test
  @DisplayName("un alta duplicada se traduce a ConflictException, no a error genérico")
  void createUser_conflict_throwsConflict() {
    expectTokenRequest();
    server
        .expect(requestTo(USERS_URI))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> client.createUser(Map.of("username", "ana")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("si Keycloak crea el usuario pero no devuelve Location, se falla explícitamente")
  void createUser_withoutLocation_fails() {
    expectTokenRequest();
    server
        .expect(requestTo(USERS_URI))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CREATED));

    assertThatThrownBy(() -> client.createUser(Map.of("username", "ana")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("identificador");
  }

  @Test
  @DisplayName("un error del IdP no propaga su cuerpo al cliente")
  void errorResponse_doesNotLeakBody() {
    expectTokenRequest();
    server
        .expect(requestTo(BASE + "/admin/realms/inventory/roles"))
        .andRespond(
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"stack\":\"detalle interno del IdP\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.realmRoles())
        .isInstanceOf(BusinessException.class)
        .hasMessageNotContaining("detalle interno");
  }

  @Test
  @DisplayName("actualizar, borrar y reasignar contraseña llegan a su endpoint")
  void writeOperations_hitTheirEndpoints() {
    expectTokenRequest();
    server
        .expect(requestTo(USERS_URI + "/" + UID))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());
    server
        .expect(requestTo(USERS_URI + "/" + UID + "/reset-password"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());
    server
        .expect(requestTo(USERS_URI + "/" + UID))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    client.updateUser(UID, Map.of("enabled", false));
    client.resetPassword(UID, "Secreta123");
    client.deleteUser(UID);

    server.verify();
  }

  @Test
  @DisplayName("una lista de roles vacía no genera ninguna llamada")
  void emptyRoleLists_produceNoRequests() {
    // Sin expectTokenRequest: si intentara llamar, ni siquiera habria token que pedir.
    client.addRealmRoles(UID, List.of());
    client.removeRealmRoles(UID, List.of());

    server.verify();
  }

  @Test
  @DisplayName("asignar y revocar roles usan POST y DELETE sobre el mismo recurso")
  void roleAssignment_usesPostAndDelete() {
    expectTokenRequest();
    String uri = USERS_URI + "/" + UID + REALM_MAPPINGS;
    server.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    server.expect(requestTo(uri)).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

    List<Map<String, Object>> roles = List.of(Map.of("id", "r1", "name", "auditor"));
    client.addRealmRoles(UID, roles);
    client.removeRealmRoles(UID, roles);

    server.verify();
  }

  @Test
  @DisplayName("un identificador que no es UUID se rechaza sin llegar a Keycloak")
  void nonUuidId_isRejectedBeforeHittingKeycloak() {
    // Prueba de seguridad, no de validacion: sin esto un id como "../../realms" haria que el
    // backend construyera una peticion contra otro recurso usando el token de servicio.
    assertThatThrownBy(() -> client.findUser("../../realms/master"))
        .isInstanceOf(ResourceNotFoundException.class);

    server.verify();
  }

  @Test
  @DisplayName("los roles de un usuario se leen de su role-mappings de realm")
  void userRealmRoles_readsRoleMappings() {
    expectTokenRequest();
    server
        .expect(requestTo(USERS_URI + "/" + UID + REALM_MAPPINGS))
        .andRespond(withSuccess("[{\"name\":\"auditor\"}]", MediaType.APPLICATION_JSON));

    assertThat(client.userRealmRoles(UID))
        .extracting(r -> r.get("name"))
        .containsExactly("auditor");
  }
}
