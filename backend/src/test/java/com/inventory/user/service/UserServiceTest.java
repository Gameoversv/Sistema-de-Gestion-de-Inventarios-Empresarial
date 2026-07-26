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
