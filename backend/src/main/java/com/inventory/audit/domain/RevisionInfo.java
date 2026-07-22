package com.inventory.audit.domain;

import com.inventory.audit.listener.EnversRevisionListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Entidad de revisión de Hibernate Envers que extiende la tabla {@code revinfo} con el nombre de
 * usuario que originó cada revisión. Es poblada automáticamente por {@link
 * com.inventory.audit.listener.EnversRevisionListener}.
 */
@Entity
@RevisionEntity(EnversRevisionListener.class)
@Table(name = "revinfo")
@Getter
@Setter
public class RevisionInfo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  private int rev;

  @RevisionTimestamp
  @Column(name = "revtstmp", nullable = false)
  private long revtstmp;

  @Column(length = 100)
  private String username;
}
