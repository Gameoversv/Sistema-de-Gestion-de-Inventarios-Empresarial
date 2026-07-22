package com.inventory.common.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint autenticado que devuelve la información del usuario actual extraída del JWT: subject,
 * roles, scopes, email, username preferido y fecha de expiración del token. Útil para depuración de
 * permisos y para que el frontend identifique al usuario.
 */
@RestController
@RequestMapping("/me")
public class MeController {

  @GetMapping
  public Map<String, Object> me(Authentication auth) {
    Map<String, Object> response = new HashMap<>();
    response.put("subject", auth.getName());
    response.put(
        "roles",
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toList());
    response.put(
        "scopes",
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("SCOPE_"))
            .map(a -> a.substring(6))
            .toList());

    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      response.put("email", jwt.getClaimAsString("email"));
      response.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
      response.put("expiresAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toString() : null);
    }

    return response;
  }
}
