package com.inventory.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.common.exception.BusinessException;
import com.inventory.user.client.KeycloakAdminClient;
import com.inventory.user.dto.UserRolesRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock KeycloakAdminClient keycloak;

  private UserService service;

  private static Map<String, Object> role(String name) {
    return Map.of("id", "r-" + name, "name", name);
  }

  private static final List<Map<String, Object>> ALL_ROLES =
      List.of(
          role("inventory-admin"),
          role("warehouse-clerk"),
          role("auditor"),
          role("viewer"),
          // Roles propios de Keycloak: el modulo no debe tocarlos nunca.
          role("offline_access"),
          role("default-roles-inventory"));

  @BeforeEach
  void setUp() {
    service = new UserService(keycloak);
  }

  @Test
  @DisplayName("solo se ofrecen como asignables los cuatro roles de negocio")
  void assignableRoles_excludesKeycloakInternals() {
    when(keycloak.realmRoles()).thenReturn(ALL_ROLES);

    assertThat(service.assignableRoles())
        .containsExactly("auditor", "inventory-admin", "viewer", "warehouse-clerk");
  }

  @Test
  @DisplayName("un rol desconocido se rechaza antes de llamar a Keycloak")
  void replaceRoles_unknownRole_isRejected() {
    assertThatThrownBy(
            () -> service.replaceRoles("u1", new UserRolesRequest(List.of("superadmin"))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("superadmin");

    verify(keycloak, never()).addRealmRoles(anyString(), any());
    verify(keycloak, never()).removeRealmRoles(anyString(), any());
  }

  @Test
  @DisplayName("el reemplazo calcula el delta: asigna lo que falta y revoca lo que sobra")
  void replaceRoles_computesDelta() {
    when(keycloak.userRealmRoles("u1")).thenReturn(List.of(role("viewer"), role("offline_access")));
    when(keycloak.realmRoles()).thenReturn(ALL_ROLES);
    when(keycloak.findUser("u1")).thenReturn(Map.of("id", "u1", "username", "u", "enabled", true));

    service.replaceRoles("u1", new UserRolesRequest(List.of("auditor")));

    ArgumentCaptor<List<Map<String, Object>>> add = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Map<String, Object>>> remove = ArgumentCaptor.forClass(List.class);
    verify(keycloak).addRealmRoles(anyString(), add.capture());
    verify(keycloak).removeRealmRoles(anyString(), remove.capture());

    assertThat(add.getValue()).extracting(r -> r.get("name")).containsExactly("auditor");
    assertThat(remove.getValue()).extracting(r -> r.get("name")).containsExactly("viewer");
  }

  @Test
  @DisplayName("los roles internos de Keycloak nunca se revocan aunque no se pidan")
  void replaceRoles_neverTouchesKeycloakInternalRoles() {
    when(keycloak.userRealmRoles("u1"))
        .thenReturn(List.of(role("offline_access"), role("default-roles-inventory")));
    when(keycloak.realmRoles()).thenReturn(ALL_ROLES);
    when(keycloak.findUser("u1")).thenReturn(Map.of("id", "u1", "username", "u", "enabled", true));

    service.replaceRoles("u1", new UserRolesRequest(List.of()));

    ArgumentCaptor<List<Map<String, Object>>> remove = ArgumentCaptor.forClass(List.class);
    verify(keycloak).removeRealmRoles(anyString(), remove.capture());
    assertThat(remove.getValue()).isEmpty();
  }

  @Test
  @DisplayName("editar sin correo conserva el que ya tenia el usuario")
  void update_withoutEmail_keepsCurrent() {
    when(keycloak.findUser("u1"))
        .thenReturn(Map.of("id", "u1", "username", "ana", "email", "previo@b.c", "enabled", true));
    when(keycloak.userRealmRoles("u1")).thenReturn(List.of());

    service.update("u1", new com.inventory.user.dto.UserUpdateRequest(null, null, null, false));

    ArgumentCaptor<Map<String, Object>> rep = ArgumentCaptor.forClass(Map.class);
    verify(keycloak).updateUser(anyString(), rep.capture());
    assertThat(rep.getValue().get("email")).isEqualTo("previo@b.c");
    assertThat(rep.getValue().get("enabled")).isEqualTo(false);
    // Nombre y apellido nulos se normalizan a cadena vacia, no se envian como null.
    assertThat(rep.getValue().get("firstName")).isEqualTo("");
  }

  @Test
  @DisplayName("el alta fija la contrasena y aplica los roles pedidos")
  void create_setsPasswordAndRoles() {
    when(keycloak.createUser(any())).thenReturn("nuevo");
    when(keycloak.userRealmRoles("nuevo")).thenReturn(List.of());
    when(keycloak.realmRoles()).thenReturn(ALL_ROLES);
    when(keycloak.findUser("nuevo"))
        .thenReturn(Map.of("id", "nuevo", "username", "ana", "enabled", true));

    service.create(
        new com.inventory.user.dto.UserCreateRequest(
            " ana ", " a@b.c ", null, null, "Secreta123", List.of("viewer")));

    verify(keycloak).resetPassword("nuevo", "Secreta123");
    ArgumentCaptor<Map<String, Object>> rep = ArgumentCaptor.forClass(Map.class);
    verify(keycloak).createUser(rep.capture());
    // Los espacios sobrantes se recortan antes de llegar al IdP.
    assertThat(rep.getValue().get("username")).isEqualTo("ana");
    assertThat(rep.getValue().get("email")).isEqualTo("a@b.c");
  }

  @Test
  @DisplayName("un alta sin roles no intenta asignar ninguno")
  void create_withNullRoles_assignsNone() {
    when(keycloak.createUser(any())).thenReturn("nuevo");
    when(keycloak.userRealmRoles("nuevo")).thenReturn(List.of());
    when(keycloak.realmRoles()).thenReturn(ALL_ROLES);
    when(keycloak.findUser("nuevo"))
        .thenReturn(Map.of("id", "nuevo", "username", "ana", "enabled", true));

    service.create(
        new com.inventory.user.dto.UserCreateRequest(
            "ana", "a@b.c", "Ana", "Perez", "Secreta123", null));

    ArgumentCaptor<List<Map<String, Object>>> add = ArgumentCaptor.forClass(List.class);
    verify(keycloak).addRealmRoles(anyString(), add.capture());
    assertThat(add.getValue()).isEmpty();
  }

  @Test
  @DisplayName("el borrado comprueba que el usuario existe antes de pedirlo")
  void delete_verifiesExistenceFirst() {
    when(keycloak.findUser("u1")).thenReturn(Map.of("id", "u1"));

    service.delete("u1");

    verify(keycloak).findUser("u1");
    verify(keycloak).deleteUser("u1");
  }

  @Test
  @DisplayName("el listado pagina traduciendo pagina y tamano a first/max")
  void list_translatesPaging() {
    when(keycloak.listUsers("ana", 40, 20)).thenReturn(List.of(Map.of("id", "u1")));
    when(keycloak.userRealmRoles("u1")).thenReturn(List.of());

    assertThat(service.list("ana", 2, 20)).hasSize(1);
    verify(keycloak).listUsers("ana", 40, 20);
  }

  @Test
  @DisplayName("la vista de usuario solo expone los roles de negocio")
  void findById_filtersInternalRoles() {
    when(keycloak.findUser("u1"))
        .thenReturn(Map.of("id", "u1", "username", "ana", "email", "a@b.c", "enabled", true));
    when(keycloak.userRealmRoles("u1"))
        .thenReturn(List.of(role("auditor"), role("offline_access")));

    var response = service.findById("u1");

    assertThat(response.username()).isEqualTo("ana");
    assertThat(response.roles()).containsExactly("auditor");
  }
}
