package com.inventory.product.repository;

import com.inventory.product.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link com.inventory.product.domain.Category}. Extiende JpaRepository con
 * métodos de búsqueda y verificación de existencia por nombre, usados para validar unicidad antes
 * de crear o actualizar categorías.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

  Optional<Category> findByName(String name);

  boolean existsByName(String name);
}
