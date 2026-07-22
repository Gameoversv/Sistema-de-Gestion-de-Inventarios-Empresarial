package com.inventory.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Añade al MDC el usuario autenticado.
 *
 * <p>No es un {@code @Component}: se inserta dentro de la cadena de Spring Security, detrás del
 * filtro que valida el token, porque antes de ese punto el {@code SecurityContext} está vacío.
 * Limpiar la clave es responsabilidad de {@link CorrelationIdFilter}, que envuelve toda la
 * petición.
 */
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

  public static final String MDC_USER = "user";
  private static final String ANONYMOUS = "anonymous";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    MDC.put(MDC_USER, resolveUsername());
    chain.doFilter(request, response);
  }

  /**
   * Prefiere {@code preferred_username} sobre el {@code sub} del token: el nombre de usuario es lo
   * que identifica a la persona en Keycloak, mientras que el subject es un UUID que no dice nada al
   * leer un log.
   */
  private String resolveUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return ANONYMOUS;
    }

    if (authentication.getPrincipal() instanceof Jwt jwt) {
      String preferred = jwt.getClaimAsString("preferred_username");
      if (preferred != null && !preferred.isBlank()) {
        return preferred;
      }
    }

    String name = authentication.getName();
    return (name == null || name.isBlank()) ? ANONYMOUS : name;
  }
}
