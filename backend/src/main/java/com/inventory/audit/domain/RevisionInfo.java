package com.inventory.audit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@RevisionEntity
@Table(name = "revinfo")
@Getter
public class RevisionInfo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  private int rev;

  @RevisionTimestamp
  @Column(name = "revtstmp", nullable = false)
  private long revtstmp;
}
