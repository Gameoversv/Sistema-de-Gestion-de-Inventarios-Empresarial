package com.inventory.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DATA-1/2 — Data Testing sobre PostgreSQL real. A diferencia de {@link ProductRepositoryIT}, que
 * vacía las tablas en cada test, aquí NO se tocan los seeds: se verifica que las migraciones Flyway
 * cargaron los datos semilla (V5), que la restricción de unicidad rechaza un SKU duplicado contra
 * un producto sembrado, y que la restricción {@code NOT NULL} del esquema se aplica de verdad —no
 * solo la validación de Bean Validation de la capa de aplicación—.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    properties = {
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri="
    })
@ActiveProfiles("test")
@Testcontainers
class DataIntegrityIT {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("inventory_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @MockBean private JwtDecoder jwtDecoder;

  @Autowired private ProductRepository productRepository;
  @Autowired private CategoryRepository categoryRepository;

  @Test
  @DisplayName("seeds — las migraciones Flyway cargaron los datos semilla (V5)")
  void seedData_loadedByMigrations() {
    assertThat(categoryRepository.count()).isGreaterThan(0);
    assertThat(productRepository.count()).isGreaterThan(0);
    // ELEC-001 es uno de los SKU sembrados en V5__seed_data.sql.
    assertThat(productRepository.findBySku("ELEC-001")).isPresent();
  }

  @Test
  @DisplayName("datos duplicados — un SKU ya sembrado se rechaza por la restricción de unicidad")
  void duplicateOfSeededSku_isRejected() {
    Product duplicate = validProduct("ELEC-001");

    assertThatThrownBy(() -> productRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("constraint — minimum_stock NOT NULL se aplica en el esquema, no solo en la app")
  void nullMinimumStock_isRejectedByNotNullConstraint() {
    Product product = validProduct("DATA-IT-NULLMIN");
    // minimumStock no lleva @NotNull en la entidad (solo @Min), asi que la validacion de la app lo
    // deja pasar; la restriccion NOT NULL de la columna es la que debe rechazarlo.
    product.setMinimumStock(null);

    assertThatThrownBy(() -> productRepository.saveAndFlush(product))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Product validProduct(String sku) {
    Category seeded = categoryRepository.findAll().get(0);
    Product p = new Product();
    p.setSku(sku);
    p.setName("Data IT product");
    p.setDescription("Data integrity test");
    p.setPrice(BigDecimal.valueOf(9.99));
    p.setStock(1);
    p.setMinimumStock(0);
    p.setActive(true);
    p.setCategory(seeded);
    return p;
  }
}
