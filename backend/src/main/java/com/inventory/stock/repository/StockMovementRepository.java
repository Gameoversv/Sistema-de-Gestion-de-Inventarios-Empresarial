package com.inventory.stock.repository;

import com.inventory.report.dto.BestSellerDto;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Repositorio JPA para {@link com.inventory.stock.domain.StockMovement}. Combina JpaRepository con
 * JpaSpecificationExecutor para filtrado dinámico. Incluye consultas por producto, tipo, rango de
 * fechas, el movimiento más reciente y carga eagerly los N más recientes con JOIN FETCH del
 * producto.
 */
public interface StockMovementRepository
    extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

  Page<StockMovement> findByProductId(Long productId, Pageable pageable);

  List<StockMovement> findByProductIdAndType(Long productId, MovementType type);

  List<StockMovement> findByCreatedAtBetween(Instant from, Instant to);

  Optional<StockMovement> findFirstByOrderByCreatedAtDesc();

  @Query("SELECT m FROM StockMovement m JOIN FETCH m.product p ORDER BY m.createdAt DESC")
  List<StockMovement> findRecent(Pageable pageable);

  /**
   * Ranking de productos más vendidos: suma de unidades de los movimientos de salida, agrupada por
   * producto y ordenada de mayor a menor.
   *
   * <p>La agregación se hace en la base y no en memoria a propósito. El equivalente en Java
   * exigiría traerse la tabla entera de movimientos —que crece sin techo, a diferencia de la de
   * productos— para quedarse con diez filas.
   *
   * <p>Solo cuenta {@code OUT}. Un {@code ADJUSTMENT} negativo corrige inventario, no es una venta,
   * y contarlo inflaría el ranking con mermas y correcciones de recuento.
   */
  @Query(
      """
      SELECT new com.inventory.report.dto.BestSellerDto(
          p.id, p.sku, p.name, SUM(m.quantity), COUNT(m))
      FROM StockMovement m
      JOIN m.product p
      WHERE m.type = com.inventory.stock.domain.StockMovement.MovementType.OUT
      GROUP BY p.id, p.sku, p.name
      ORDER BY SUM(m.quantity) DESC
      """)
  List<BestSellerDto> findBestSellers(Pageable pageable);
}
