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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    properties = {
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
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate tx;
  private Category category;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(transactionManager);
    tx.execute(
        status -> {
          productRepository.deleteAll();
          categoryRepository.deleteAll();
          category = new Category();
          category.setName("Audit-Cat-" + System.nanoTime());
          category.setDescription("Audit IT category");
          return categoryRepository.save(category);
        });
  }

  // Verifica que al crear un producto Envers registra exactamente una revisión en BD.
  @Test
  @DisplayName("create product — Envers records exactly one revision")
  void createProduct_createsOneEnversRevision() {
    Long id =
        tx.execute(
            status -> {
              Product p = buildProduct("SKU-" + System.nanoTime(), "Auditable Widget", 5);
              return productRepository.saveAndFlush(p).getId();
            });

    List<?> revisions =
        tx.execute(status -> AuditReaderFactory.get(entityManager).getRevisions(Product.class, id));

    assertThat(revisions).hasSize(1);
  }

  // Verifica que al crear y luego actualizar un producto Envers registra dos revisiones.
  @Test
  @DisplayName("create then update product — Envers records two revisions")
  void createThenUpdateProduct_createsTwoEnversRevisions() {
    Long id =
        tx.execute(
            status -> {
              Product p = buildProduct("SKU-" + System.nanoTime(), "Widget v1", 10);
              return productRepository.saveAndFlush(p).getId();
            });

    tx.execute(
        status -> {
          Product p = productRepository.findById(id).orElseThrow();
          p.setName("Widget v2");
          return productRepository.saveAndFlush(p);
        });

    List<?> revisions =
        tx.execute(status -> AuditReaderFactory.get(entityManager).getRevisions(Product.class, id));

    assertThat(revisions).hasSize(2);
  }

  // Verifica que al desactivar un producto Envers captura el estado con active=false.
  @Test
  @DisplayName("deactivate product — Envers captures state change in new revision")
  void deactivateProduct_capturesRevisionWithActiveFalse() {
    Long id =
        tx.execute(
            status -> {
              Product p = buildProduct("SKU-" + System.nanoTime(), "Deactivatable Widget", 3);
              return productRepository.saveAndFlush(p).getId();
            });

    tx.execute(
        status -> {
          Product p = productRepository.findById(id).orElseThrow();
          p.setActive(false);
          return productRepository.saveAndFlush(p);
        });

    List<?> revisions =
        tx.execute(status -> AuditReaderFactory.get(entityManager).getRevisions(Product.class, id));

    assertThat(revisions).hasSize(2);

    Product auditedState =
        tx.execute(
            status -> {
              List<?> revs = AuditReaderFactory.get(entityManager).getRevisions(Product.class, id);
              Number latestRev = (Number) revs.get(revs.size() - 1);
              return AuditReaderFactory.get(entityManager).find(Product.class, id, latestRev);
            });

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
