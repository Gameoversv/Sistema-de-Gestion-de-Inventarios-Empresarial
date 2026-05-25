package com.inventory.product.repository;

import com.inventory.product.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
