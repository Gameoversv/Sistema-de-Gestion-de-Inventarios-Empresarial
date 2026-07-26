package com.inventory.user.dto;

import java.util.List;

/** Vista de un usuario del realm. No expone credenciales ni atributos internos del IdP. */
public record UserResponse(
    String id,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean enabled,
    List<String> roles) {}
