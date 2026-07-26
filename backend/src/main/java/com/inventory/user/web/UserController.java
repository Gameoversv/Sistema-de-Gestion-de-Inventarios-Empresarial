package com.inventory.user.web;

import com.inventory.user.dto.UserCreateRequest;
import com.inventory.user.dto.UserResponse;
import com.inventory.user.dto.UserRolesRequest;
import com.inventory.user.dto.UserUpdateRequest;
import com.inventory.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de usuarios, roles y permisos.
 *
 * <p>Cierra el último hueco de la matriz mínima obligatoria del enunciado: {@code user:manage}
 * existía en el realm y en la documentación pero <strong>no protegía ningún endpoint</strong>. Era
 * el único de los siete permisos sin operación detrás.
 *
 * <p>Todas las operaciones exigen {@code SCOPE_user:manage}, que por los scope-mappings del realm
 * solo puede obtener {@code inventory-admin}.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuarios", description = "Gestión de usuarios, roles y permisos del realm")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasAuthority('SCOPE_user:manage')")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Listar usuarios del realm con sus roles")
  @ApiResponse(responseCode = "200", description = "Lista de usuarios")
  @ApiResponse(responseCode = "401", description = "Token ausente o inválido")
  @ApiResponse(responseCode = "403", description = "Falta el permiso user:manage")
  public List<UserResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, page, size);
  }

  @GetMapping("/roles")
  @Operation(summary = "Roles de realm que este módulo permite asignar")
  public List<String> assignableRoles() {
    return service.assignableRoles();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consultar un usuario por identificador")
  @ApiResponse(responseCode = "404", description = "El usuario no existe en el realm")
  public UserResponse findById(@PathVariable String id) {
    return service.findById(id);
  }

  @PostMapping
  @Operation(summary = "Crear un usuario con su contraseña y sus roles")
  @ApiResponse(responseCode = "201", description = "Usuario creado")
  @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese nombre o correo")
  public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Editar los datos de un usuario y su estado activo/inactivo")
  public UserResponse update(
      @PathVariable String id, @Valid @RequestBody UserUpdateRequest request) {
    return service.update(id, request);
  }

  @PutMapping("/{id}/roles")
  @Operation(
      summary = "Reemplazar los roles de un usuario",
      description =
          "El conjunto enviado sustituye al actual: los roles que no aparezcan se revocan.")
  public UserResponse replaceRoles(
      @PathVariable String id, @Valid @RequestBody UserRolesRequest request) {
    return service.replaceRoles(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Eliminar un usuario del realm")
  @ApiResponse(responseCode = "204", description = "Usuario eliminado")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
