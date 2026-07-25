package com.inventory.product.repository;

import com.inventory.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio JPA para {@link com.inventory.product.domain.Product}. Combina JpaRepository con
 * JpaSpecificationExecutor para filtrado dinámico. Incluye búsqueda por SKU, texto libre,
 * categoría, productos con stock bajo o en cero, y bloqueo pesimista para actualizaciones
 * concurrentes de stock.
 */
public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  Optional<Product> findBySku(String sku);

  boolean existsBySku(String sku);

  Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

  @Query(
      "SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))"
          + " OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))")
  Page<Product> search(@Param("q") String query, Pageable pageable);

  @Query(
      "SELECT p FROM Product p WHERE p.active = true AND p.stock <= p.minimumStock ORDER BY p.stock"
          + " ASC")
  List<Product> findLowStockProducts();

  @Query("SELECT p FROM Product p WHERE p.active = true AND p.stock = 0 ORDER BY p.name ASC")
  List<Product> findCriticalStockProducts();

  /**
   * Cuenta —sin materializar entidades— los productos bajo mínimo. La consume el gauge {@code
   * inventory_products_below_minimum}, que se evalúa en cada scrape de Prometheus.
   */
  @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.stock <= p.minimumStock")
  long countLowStockProducts();

  /**
   * Productos activos ordenados por valor inmovilizado (precio × stock), con el orden y el recorte
   * resueltos en la base.
   *
   * <p>Antes el ranking se armaba trayendo la tabla entera con {@code findAll()} y ordenándola en
   * memoria para quedarse con diez filas, en un endpoint que el dashboard llama en cada carga.
   *
   * <p>El {@code LEFT JOIN FETCH} de la categoría evita el N+1 al pintar el nombre de cada fila, y
   * es un {@code LEFT} porque los productos sin categoría también entran en el ranking; un join
   * interno los habría dejado fuera sin que se notara.
   */
  @Query(
      """
      SELECT p FROM Product p
      LEFT JOIN FETCH p.category
      WHERE p.active = true
      ORDER BY p.price * p.stock DESC
      """)
  List<Product> findTopByInventoryValue(Pageable pageable);

  /** Mismo ranking que {@link #findTopByInventoryValue}, ordenado por unidades en existencia. */
  @Query(
      """
      SELECT p FROM Product p
      LEFT JOIN FETCH p.category
      WHERE p.active = true
      ORDER BY p.stock DESC
      """)
  List<Product> findTopByStock(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
