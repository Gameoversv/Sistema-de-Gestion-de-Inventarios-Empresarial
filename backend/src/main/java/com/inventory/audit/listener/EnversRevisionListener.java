package com.inventory.audit.listener;

import com.inventory.audit.domain.RevisionInfo;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Listener de Hibernate Envers que se ejecuta en cada nueva revisión y captura el nombre de usuario
 * del token JWT activo en el {@code SecurityContext}, grabándolo en {@link
 * com.inventory.audit.domain.RevisionInfo} para trazabilidad completa.
 */
public class EnversRevisionListener implements RevisionListener {

  @Override
  public void newRevision(Object revisionEntity) {
    RevisionInfo info = (RevisionInfo) revisionEntity;
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String username = jwt.getClaimAsString("preferred_username");
      info.setUsername((username != null && !username.isBlank()) ? username : jwtAuth.getName());
    } else if (auth != null && auth.getName() != null) {
      info.setUsername(auth.getName());
    }
  }
}
