package com.inventory.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alta de usuario. La contraseña es obligatoria y se fija como no temporal: un usuario creado sin
 * ella queda inutilizable y hay que ir a la consola de Keycloak a arreglarlo.
 */
public record UserCreateRequest(
    @NotBlank @Size(min = 3, max = 60) String username,
    @NotBlank @Email String email,
    @Size(max = 60) String firstName,
    @Size(max = 60) String lastName,
    @NotBlank @Size(min = 8, max = 100) String password,
    List<String> roles) {}
