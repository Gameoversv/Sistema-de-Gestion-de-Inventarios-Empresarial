package com.inventory.stock.domain;

import com.inventory.common.domain.BaseEntity;
import com.inventory.product.domain.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Entidad JPA que registra cada movimiento de inventario sobre un producto. Soporta tres tipos:
 * entrada (IN), salida (OUT) y ajuste (ADJUSTMENT). Almacena el stock antes y después del
 * movimiento, el usuario que lo realizó y una referencia documental opcional. Auditada por
 * Hibernate Envers.
 */
@Entity
@Table(name = "stock_movements")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends BaseEntity {

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MovementType type;

  @NotNull
  @Min(0)
  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "quantity_before")
  private Integer quantityBefore;

  @Column(name = "quantity_after")
  private Integer quantityAfter;

  @Column(length = 100)
  private String performedBy;

  @Column(length = 500)
  private String reason;

  @Column(name = "reference_id")
  private String referenceId;

  public enum MovementType {
    IN,
    OUT,
    ADJUSTMENT
  }
}
