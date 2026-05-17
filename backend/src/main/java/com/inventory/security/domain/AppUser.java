package com.inventory.security.domain;

import com.inventory.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "app_users")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends BaseEntity {

  @NotNull
  @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
  private UUID keycloakId;

  @NotBlank
  @Email
  @Size(max = 255)
  @Column(nullable = false, unique = true)
  private String email;

  @Size(max = 100)
  @Column(name = "display_name", length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private Role role = Role.VIEWER;

  @Column(nullable = false)
  @Builder.Default
  private boolean enabled = true;

  public enum Role {
    ADMIN,
    MANAGER,
    VIEWER
  }
}
