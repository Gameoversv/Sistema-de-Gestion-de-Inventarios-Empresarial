package com.inventory.product.domain;

import com.inventory.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "products")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, unique = true, length = 100)
  private String sku;

  @NotBlank
  @Size(max = 255)
  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @NotNull
  @DecimalMin("0.00")
  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

  @NotNull
  @Min(0)
  @Column(nullable = false)
  private Integer stock;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;
}
