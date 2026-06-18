package com.inventory.security.repository;

import com.inventory.security.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link com.inventory.security.domain.AppUser}. Provee búsqueda y
 * verificación de existencia por keycloakId (UUID) y email, usados para sincronización de usuarios
 * con Keycloak y validación de unicidad.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  Optional<AppUser> findByKeycloakId(UUID keycloakId);

  Optional<AppUser> findByEmail(String email);

  boolean existsByKeycloakId(UUID keycloakId);

  boolean existsByEmail(String email);
}
