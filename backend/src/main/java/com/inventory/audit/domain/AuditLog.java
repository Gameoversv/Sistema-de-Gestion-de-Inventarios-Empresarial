package com.inventory.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Registro de auditoría manual que captura qué acción (CREATE, UPDATE, DELETE) realizó un usuario
 * sobre una entidad específica. Se persiste de forma asíncrona mediante {@link
 * com.inventory.audit.service.AuditService}.
 */
@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String entityType;

  @Column(nullable = false)
  private Long entityId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Action action;

  @Column(name = "performed_by", length = 100)
  private String performedBy;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public enum Action {
    CREATE,
    UPDATE,
    DELETE
  }
}
