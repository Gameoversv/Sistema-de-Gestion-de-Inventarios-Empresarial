package com.inventory.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Edición de los datos de un usuario. El nombre de usuario no se cambia: es su identidad. */
public record UserUpdateRequest(
    @Email String email,
    @Size(max = 60) String firstName,
    @Size(max = 60) String lastName,
    @NotNull Boolean enabled) {}
