package com.inventory.stock.domain;

import com.inventory.common.domain.BaseEntity;
import com.inventory.product.domain.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "stock_movements")
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
  @Column(nullable = false)
  private Integer quantity;

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
