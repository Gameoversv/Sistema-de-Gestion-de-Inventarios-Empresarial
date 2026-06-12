package com.inventory.security.repository;

import com.inventory.security.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  Optional<AppUser> findByKeycloakId(UUID keycloakId);

  Optional<AppUser> findByEmail(String email);

  boolean existsByKeycloakId(UUID keycloakId);

  boolean existsByEmail(String email);
}
