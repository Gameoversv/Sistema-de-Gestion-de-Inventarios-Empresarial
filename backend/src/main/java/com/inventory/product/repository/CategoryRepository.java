package com.inventory.product.repository;

import com.inventory.product.domain.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  Optional<Category> findByName(String name);

  boolean existsByName(String name);
}
