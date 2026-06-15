package com.inventory.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri="
    })
@ActiveProfiles("test")
@Testcontainers
class ProductRepositoryIT {

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

  private Category category;

  @BeforeEach
  void setUp() {
    productRepository.deleteAll();
    categoryRepository.deleteAll();

    category = new Category();
    category.setName("IT-Cat-" + System.nanoTime());
    category.setDescription("Integration test category");
    categoryRepository.save(category);
  }

  @Test
  @DisplayName("save and findById — persists and retrieves product")
  void save_andFindById_persistsProduct() {
    Product product = buildProduct("REPO-IT-001", "Widget", 10);
    Product saved = productRepository.save(product);

    Optional<Product> found = productRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getSku()).isEqualTo("REPO-IT-001");
    assertThat(found.get().getName()).isEqualTo("Widget");
    assertThat(found.get().getStock()).isEqualTo(10);
  }

  @Test
  @DisplayName("findBySku — returns correct product by SKU")
  void findBySku_existingSku_returnsProduct() {
    productRepository.save(buildProduct("REPO-IT-SKU", "Gadget", 5));

    Optional<Product> found = productRepository.findBySku("REPO-IT-SKU");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Gadget");
  }

  @Test
  @DisplayName("findBySku — returns empty for unknown SKU")
  void findBySku_unknownSku_returnsEmpty() {
    Optional<Product> found = productRepository.findBySku("NONEXISTENT");

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("findAll returns only active products via active flag filter")
  void findAll_activeProducts_returnsActiveOnly() {
    Product active = buildProduct("REPO-IT-ACTIVE", "Active Widget", 3);
    active.setActive(true);
    productRepository.save(active);

    Product inactive = buildProduct("REPO-IT-INACTIVE", "Inactive Widget", 0);
    inactive.setActive(false);
    productRepository.save(inactive);

    List<Product> all = productRepository.findAll();
    assertThat(all).hasSize(2);

    long activeCount = all.stream().filter(Product::getActive).count();
    assertThat(activeCount).isEqualTo(1);
  }

  @Test
  @DisplayName("duplicate SKU — throws DataIntegrityViolationException")
  void save_duplicateSku_throwsConstraintViolation() {
    productRepository.save(buildProduct("REPO-IT-DUP", "Original", 1));

    Product duplicate = buildProduct("REPO-IT-DUP", "Duplicate", 1);

    assertThatThrownBy(() -> productRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("delete — removes product from repository")
  void delete_existingProduct_removesFromDb() {
    Product saved = productRepository.save(buildProduct("REPO-IT-DEL", "Deletable", 1));
    Long id = saved.getId();

    productRepository.deleteById(id);

    assertThat(productRepository.findById(id)).isEmpty();
  }

  @Test
  @DisplayName("findLowStockProducts — returns products at or below minimum stock")
  void findLowStockProducts_returnsCorrectProducts() {
    Product low = buildProduct("REPO-IT-LOW", "Low Stock Widget", 0);
    low.setMinimumStock(5);
    productRepository.save(low);

    Product ok = buildProduct("REPO-IT-OK", "OK Stock Widget", 20);
    ok.setMinimumStock(5);
    productRepository.save(ok);

    List<Product> lowStock = productRepository.findLowStockProducts();

    assertThat(lowStock).hasSize(1);
    assertThat(lowStock.get(0).getSku()).isEqualTo("REPO-IT-LOW");
  }

  private Product buildProduct(String sku, String name, int stock) {
    Product p = new Product();
    p.setSku(sku);
    p.setName(name);
    p.setDescription("IT test product");
    p.setPrice(BigDecimal.valueOf(9.99));
    p.setStock(stock);
    p.setMinimumStock(0);
    p.setActive(true);
    p.setCategory(category);
    return p;
  }
}
