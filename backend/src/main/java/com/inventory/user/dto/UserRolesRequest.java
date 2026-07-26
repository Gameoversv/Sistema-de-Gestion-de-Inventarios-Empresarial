package com.inventory.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Conjunto de roles de realm que debe tener el usuario tras la operación. Es un reemplazo, no un
 * añadido: el servicio calcula qué asignar y qué revocar. Una lista vacía deja al usuario sin
 * ningún rol, que es una decisión válida y explícita.
 */
public record UserRolesRequest(@NotNull List<String> roles) {}
