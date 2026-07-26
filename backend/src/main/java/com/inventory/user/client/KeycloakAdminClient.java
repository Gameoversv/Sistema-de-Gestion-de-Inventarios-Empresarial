package com.inventory.user.client;

import com.inventory.common.exception.BusinessException;
import com.inventory.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Cliente de la Admin API de Keycloak. Es la pieza que da contenido real al permiso {@code
 * user:manage}: sin ella el permiso existía en el realm y en la matriz del README pero no protegía
 * ningún endpoint, que es la configuración decorativa que prohíbe la regla 3 del plan.
 *
 * <p>Se autentica como <strong>service account</strong> de {@code inventory-backend} por {@code
 * client_credentials}. Ese cliente pasó a confidencial en G-1 para poder declarar Authorization
 * Services, así que la capacidad ya estaba; aquí se usa.
 *
 * <p>El token de servicio se cachea hasta 30 s antes de su expiración. Sin caché, cada operación de
 * usuarios costaría dos viajes a Keycloak en vez de uno.
 */
@Component
public class KeycloakAdminClient {

  private final RestClient http;
  private final String realm;
  private final String clientId;
  private final String clientSecret;
  private final String baseUrl;

  private String cachedToken;
  private Instant cachedTokenExpiry = Instant.EPOCH;

  public KeycloakAdminClient(
      RestClient.Builder builder,
      @Value("${app.keycloak.admin.base-url}") String baseUrl,
      @Value("${app.keycloak.admin.realm}") String realm,
      @Value("${app.keycloak.admin.client-id}") String clientId,
      @Value("${app.keycloak.admin.client-secret}") String clientSecret) {
    this.baseUrl = baseUrl;
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.http = builder.build();
  }

  // ── Token de servicio ──────────────────────────────────────────────────────

  private synchronized String serviceToken() {
    if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
      return cachedToken;
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new BusinessException(
          "La gestión de usuarios no está configurada: falta KC_BACKEND_CLIENT_SECRET");
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);

    Map<?, ?> body =
        http.post()
            .uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> failService(res.getStatusCode()))
            .body(Map.class);

    if (body == null || body.get("access_token") == null) {
      throw new BusinessException("Keycloak no devolvió token de servicio");
    }
    cachedToken = String.valueOf(body.get("access_token"));
    long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60L;
    cachedTokenExpiry = Instant.now().plusSeconds(Math.max(expiresIn - 30, 5));
    return cachedToken;
  }

  private void failService(HttpStatusCode status) {
    // No se propaga el cuerpo: puede contener detalle del IdP que no interesa al cliente.
    throw new BusinessException(
        "No se pudo autenticar contra Keycloak para gestionar usuarios (" + status + ")");
  }

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String USERS = "/users/";
  private static final String REALM_ROLE_MAPPINGS = "/role-mappings/realm";

  /**
   * Los identificadores de Keycloak son UUID. Se valida antes de meterlos en la ruta de la Admin
   * API porque llegan del cliente HTTP: sin esta comprobacion, un id como {@code ../../realms} deja
   * al backend construyendo una peticion contra un recurso distinto del que cree, usando el token
   * de servicio, que tiene mas permisos que el usuario que la origina.
   */
  private static String requireUserId(String id) {
    if (id == null || !UUID_PATTERN.matcher(id).matches()) {
      throw new ResourceNotFoundException("Usuario no encontrado: " + id);
    }
    return id;
  }

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private RestClient.RequestHeadersSpec<?> get(String path) {
    return http.get().uri(adminUri(path)).header(AUTH_HEADER, BEARER + serviceToken());
  }

  private String adminUri(String path) {
    return baseUrl + "/admin/realms/" + realm + path;
  }

  // ── Usuarios ───────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listUsers(String search, int first, int max) {
    String uri = adminUri("/users") + "?first=" + first + "&max=" + max;
    if (search != null && !search.isBlank()) {
      uri = uri + "&search=" + search.trim();
    }
    return http.get()
        .uri(uri)
        .header(AUTH_HEADER, BEARER + serviceToken())
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, (req, res) -> fail("listar usuarios", res.getStatusCode()))
        .body(List.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> findUser(String id) {
    Map<String, Object> user =
        get(USERS + requireUserId(id))
            .retrieve()
            .onStatus(
                s -> s.value() == 404,
                (req, res) -> {
                  throw new ResourceNotFoundException("Usuario no encontrado: " + id);
                })
            .onStatus(
                HttpStatusCode::isError,
                (req, res) -> fail("consultar usuario", res.getStatusCode()))
            .body(Map.class);
    if (user == null) {
      throw new ResourceNotFoundException("Usuario no encontrado: " + id);
    }
    return user;
  }

  /** Devuelve el id del usuario creado, extraído de la cabecera Location. */
  public String createUser(Map<String, Object> representation) {
    var response =
        http.post()
            .uri(adminUri("/users"))
            .header(AUTH_HEADER, BEARER + serviceToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(representation)
            .retrieve()
            .onStatus(
                s -> s.value() == 409,
                (req, res) -> {
                  throw new com.inventory.common.exception.ConflictException(
                      "Ya existe un usuario con ese nombre o correo");
                })
            .onStatus(
                HttpStatusCode::isError, (req, res) -> fail("crear usuario", res.getStatusCode()))
            .toBodilessEntity();

    var location = response.getHeaders().getLocation();
    if (location == null) {
      throw new BusinessException("Keycloak creó el usuario pero no devolvió su identificador");
    }
    String path = location.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  public void updateUser(String id, Map<String, Object> representation) {
    http.put()
        .uri(adminUri(USERS + requireUserId(id)))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(representation)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, (req, res) -> fail("actualizar usuario", res.getStatusCode()))
        .toBodilessEntity();
  }

  public void deleteUser(String id) {
    http.delete()
        .uri(adminUri(USERS + requireUserId(id)))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, (req, res) -> fail("eliminar usuario", res.getStatusCode()))
        .toBodilessEntity();
  }

  public void resetPassword(String id, String password) {
    http.put()
        .uri(adminUri(USERS + requireUserId(id) + "/reset-password"))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("type", "password", "value", password, "temporary", false))
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, (req, res) -> fail("asignar contraseña", res.getStatusCode()))
        .toBodilessEntity();
  }

  // ── Roles de realm ─────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> realmRoles() {
    return http.get()
        .uri(adminUri("/roles"))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail("listar roles", res.getStatusCode()))
        .body(List.class);
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> userRealmRoles(String id) {
    return http.get()
        .uri(adminUri(USERS + requireUserId(id) + REALM_ROLE_MAPPINGS))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, (req, res) -> fail("consultar roles", res.getStatusCode()))
        .body(List.class);
  }

  public void addRealmRoles(String id, List<Map<String, Object>> roles) {
    if (roles.isEmpty()) {
      return;
    }
    http.post()
        .uri(adminUri(USERS + requireUserId(id) + REALM_ROLE_MAPPINGS))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(roles)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail("asignar roles", res.getStatusCode()))
        .toBodilessEntity();
  }

  public void removeRealmRoles(String id, List<Map<String, Object>> roles) {
    if (roles.isEmpty()) {
      return;
    }
    http.method(org.springframework.http.HttpMethod.DELETE)
        .uri(adminUri(USERS + requireUserId(id) + REALM_ROLE_MAPPINGS))
        .header(AUTH_HEADER, BEARER + serviceToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(roles)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail("revocar roles", res.getStatusCode()))
        .toBodilessEntity();
  }

  private void fail(String accion, HttpStatusCode status) {
    throw new BusinessException("Keycloak rechazó la operación al " + accion + " (" + status + ")");
  }
}
