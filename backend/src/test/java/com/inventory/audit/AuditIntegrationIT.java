package com.inventory.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
class AuditIntegrationIT {

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
  @Autowired private EntityManager entityManager;

  private Category category;

  @BeforeEach
  void setUp() {
    productRepository.deleteAll();
    categoryRepository.deleteAll();

    category = new Category();
    category.setName("Audit-Cat-" + System.nanoTime());
    category.setDescription("Audit IT category");
    categoryRepository.save(category);
  }

  @Test
  @DisplayName("create product — Envers records exactly one revision")
  @Transactional
  void createProduct_createsOneEnversRevision() {
    Product product = buildProduct("AUDIT-IT-001", "Auditable Widget", 5);
    Product saved = productRepository.saveAndFlush(product);

    List<?> revisions =
        AuditReaderFactory.get(entityManager).getRevisions(Product.class, saved.getId());

    assertThat(revisions).hasSize(1);
  }

  @Test
  @DisplayName("create then update product — Envers records two revisions")
  @Transactional
  void createThenUpdateProduct_createsTwoEnversRevisions() {
    Product product = buildProduct("AUDIT-IT-002", "Widget v1", 10);
    Product saved = productRepository.saveAndFlush(product);

    saved.setName("Widget v2");
    productRepository.saveAndFlush(saved);

    List<?> revisions =
        AuditReaderFactory.get(entityManager).getRevisions(Product.class, saved.getId());

    assertThat(revisions).hasSize(2);
  }

  @Test
  @DisplayName("deactivate product — Envers captures state change in new revision")
  @Transactional
  void deactivateProduct_capturesRevisionWithActiveFalse() {
    Product product = buildProduct("AUDIT-IT-003", "Deactivatable Widget", 3);
    Product saved = productRepository.saveAndFlush(product);

    saved.setActive(false);
    productRepository.saveAndFlush(saved);

    List<?> revisions =
        AuditReaderFactory.get(entityManager).getRevisions(Product.class, saved.getId());

    assertThat(revisions).hasSize(2);

    Number latestRev = (Number) revisions.get(revisions.size() - 1);
    Product auditedState =
        AuditReaderFactory.get(entityManager)
            .find(Product.class, saved.getId(), latestRev);

    assertThat(auditedState.getActive()).isFalse();
  }

  private Product buildProduct(String sku, String name, int stock) {
    Product p = new Product();
    p.setSku(sku);
    p.setName(name);
    p.setDescription("Audit IT test product");
    p.setPrice(BigDecimal.valueOf(19.99));
    p.setStock(stock);
    p.setMinimumStock(0);
    p.setActive(true);
    p.setCategory(category);
    return p;
  }
}
