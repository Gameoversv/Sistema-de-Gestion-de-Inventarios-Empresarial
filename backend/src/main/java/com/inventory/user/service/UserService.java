package com.inventory.user.service;

import com.inventory.common.exception.BusinessException;
import com.inventory.user.client.KeycloakAdminClient;
import com.inventory.user.dto.UserCreateRequest;
import com.inventory.user.dto.UserResponse;
import com.inventory.user.dto.UserRolesRequest;
import com.inventory.user.dto.UserUpdateRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gestión de usuarios, roles y permisos — el módulo "Seguridad" de la matriz del enunciado, que
 * hasta ahora no tenía implementación y dejaba {@code user:manage} sin proteger nada.
 *
 * <p>El almacén de identidades es Keycloak, no la base de datos: duplicar usuarios en local crearía
 * dos verdades que se desincronizan. Este servicio traduce entre los DTO de la API y las
 * representaciones de la Admin API.
 */
@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  /**
   * Roles que este módulo administra. Se restringe a propósito a los cuatro del negocio: el realm
   * trae además roles propios de Keycloak ({@code offline_access}, {@code uma_authorization},
   * {@code default-roles-*}) que no significan nada aquí y que tocar por accidente sí rompería la
   * sesión del usuario.
   */
  private static final Set<String> MANAGED_ROLES =
      Set.of("inventory-admin", "warehouse-clerk", "auditor", "viewer");

  private static final String EMAIL = "email";
  private static final String FIRST_NAME = "firstName";
  private static final String LAST_NAME = "lastName";
  private static final String ENABLED = "enabled";

  private final KeycloakAdminClient keycloak;

  public UserService(KeycloakAdminClient keycloak) {
    this.keycloak = keycloak;
  }

  // ── Consulta ───────────────────────────────────────────────────────────────

  public List<UserResponse> list(String search, int page, int size) {
    return keycloak.listUsers(search, page * size, size).stream().map(this::toResponse).toList();
  }

  public UserResponse findById(String id) {
    return toResponse(keycloak.findUser(id));
  }

  /** Roles de realm que este módulo permite asignar, para poblar el selector del frontend. */
  public List<String> assignableRoles() {
    return keycloak.realmRoles().stream()
        .map(r -> String.valueOf(r.get("name")))
        .filter(MANAGED_ROLES::contains)
        .sorted()
        .toList();
  }

  // ── Escritura ──────────────────────────────────────────────────────────────

  public UserResponse create(UserCreateRequest request) {
    validateRoles(request.roles());

    Map<String, Object> representation = new HashMap<>();
    representation.put("username", request.username().trim());
    representation.put(EMAIL, request.email().trim());
    representation.put(FIRST_NAME, nullSafe(request.firstName()));
    representation.put(LAST_NAME, nullSafe(request.lastName()));
    representation.put(ENABLED, true);
    representation.put("emailVerified", true);

    String id = keycloak.createUser(representation);
    keycloak.resetPassword(id, request.password());
    replaceRoles(id, request.roles() == null ? List.of() : request.roles());

    log.info(
        "Usuario creado en el realm: username={} id={}", forLog(request.username()), forLog(id));
    return findById(id);
  }

  public UserResponse update(String id, UserUpdateRequest request) {
    Map<String, Object> current = keycloak.findUser(id);

    Map<String, Object> representation = new HashMap<>();
    representation.put(
        "email", request.email() != null ? request.email().trim() : current.get(EMAIL));
    representation.put(FIRST_NAME, nullSafe(request.firstName()));
    representation.put(LAST_NAME, nullSafe(request.lastName()));
    representation.put(ENABLED, request.enabled());

    keycloak.updateUser(id, representation);
    log.info("Usuario actualizado: id={} enabled={}", forLog(id), request.enabled());
    return findById(id);
  }

  public UserResponse replaceRoles(String id, UserRolesRequest request) {
    validateRoles(request.roles());
    replaceRoles(id, request.roles());
    log.info("Roles reemplazados: id={} roles={}", forLog(id), request.roles());
    return findById(id);
  }

  public void delete(String id) {
    keycloak.findUser(id);
    keycloak.deleteUser(id);
    log.info("Usuario eliminado del realm: id={}", forLog(id));
  }

  // ── Interno ────────────────────────────────────────────────────────────────

  /**
   * Reemplaza el conjunto de roles gestionados. Calcula el delta en vez de revocar y volver a
   * asignar: así una llamada que no cambia nada no genera eventos de seguridad en Keycloak.
   */
  private void replaceRoles(String id, List<String> desired) {
    Set<String> target = new LinkedHashSet<>(desired);

    List<Map<String, Object>> currentAll = keycloak.userRealmRoles(id);
    Set<String> currentManaged =
        currentAll.stream()
            .map(r -> String.valueOf(r.get("name")))
            .filter(MANAGED_ROLES::contains)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    List<Map<String, Object>> available = keycloak.realmRoles();

    List<Map<String, Object>> toAdd = new ArrayList<>();
    for (String name : target) {
      if (!currentManaged.contains(name)) {
        available.stream()
            .filter(r -> name.equals(r.get("name")))
            .findFirst()
            .ifPresent(toAdd::add);
      }
    }

    List<Map<String, Object>> toRemove = new ArrayList<>();
    for (Map<String, Object> role : currentAll) {
      String name = String.valueOf(role.get("name"));
      if (MANAGED_ROLES.contains(name) && !target.contains(name)) {
        toRemove.add(role);
      }
    }

    keycloak.addRealmRoles(id, toAdd);
    keycloak.removeRealmRoles(id, toRemove);
  }

  private void validateRoles(List<String> roles) {
    if (roles == null) {
      return;
    }
    List<String> unknown = roles.stream().filter(r -> !MANAGED_ROLES.contains(r)).toList();
    if (!unknown.isEmpty()) {
      throw new BusinessException("Roles no gestionables por este módulo: " + unknown);
    }
  }

  @SuppressWarnings("unchecked")
  private UserResponse toResponse(Map<String, Object> user) {
    String id = String.valueOf(user.get("id"));
    List<String> roles =
        keycloak.userRealmRoles(id).stream()
            .map(r -> String.valueOf(r.get("name")))
            .filter(MANAGED_ROLES::contains)
            .sorted()
            .toList();

    return new UserResponse(
        id,
        (String) user.get("username"),
        (String) user.get(EMAIL),
        (String) user.get(FIRST_NAME),
        (String) user.get(LAST_NAME),
        Boolean.TRUE.equals(user.get(ENABLED)),
        roles);
  }

  /**
   * Quita saltos de linea antes de registrar valores que vienen del cliente. Sin esto, un nombre de
   * usuario con CR/LF puede fabricar lineas de log falsas y ensuciar la pista de auditoria.
   */
  private static String forLog(String value) {
    return value == null ? "" : value.replaceAll("[\r\n]", "_");
  }

  private String nullSafe(String value) {
    return value == null ? "" : value.trim();
  }
}
