package com.inventory.product.domain;

import com.inventory.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Entidad JPA que representa una categoría de productos del inventario. El nombre debe ser único.
 * Todas las modificaciones quedan registradas automáticamente por Hibernate Envers para
 * trazabilidad de cambios.
 */
@Entity
@Table(name = "categories")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;
}
