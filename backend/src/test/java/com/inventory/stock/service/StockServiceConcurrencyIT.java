package com.inventory.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.common.exception.BusinessException;
import com.inventory.product.domain.Category;
import com.inventory.product.domain.Product;
import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri="
    })
@ActiveProfiles("test")
@Testcontainers
class StockServiceConcurrencyIT {

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

  @Autowired private StockService stockService;
  @Autowired private ProductRepository productRepository;
  @Autowired private CategoryRepository categoryRepository;

  private Long productId;
  private static final int INITIAL_STOCK = 10;
  private static final Authentication AUTH =
      new UsernamePasswordAuthenticationToken("concurrency-tester", null);

  @BeforeEach
  void setUp() {
    productRepository.deleteAll();
    categoryRepository.deleteAll();

    Category category = new Category();
    category.setName("Test-Cat-" + System.nanoTime());
    category.setDescription("test");
    categoryRepository.save(category);

    Product product = new Product();
    product.setSku("CONC-" + System.nanoTime());
    product.setName("Concurrent Widget");
    product.setDescription("test");
    product.setPrice(BigDecimal.ONE);
    product.setStock(INITIAL_STOCK);
    product.setMinimumStock(0);
    product.setActive(true);
    product.setCategory(category);
    productId = productRepository.save(product).getId();
  }

  @Test
  @DisplayName(
      "10 concurrent OUT(1) — stock never goes negative, all successful or BusinessException")
  void concurrentOutRequests_stockNeverNegative() throws InterruptedException, ExecutionException {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  stockService.registerMovement(
                      new StockMovementRequest(
                          productId, MovementType.OUT, 1, "concurrent-test", null),
                      AUTH);
                  successCount.incrementAndGet();
                } catch (BusinessException ex) {
                  failCount.incrementAndGet();
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                } finally {
                  doneLatch.countDown();
                }
              }));
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).as("All threads completed within timeout").isTrue();

    // Sin esto, una excepción distinta de BusinessException moría dentro de su Future y el test
    // fallaba luego en el invariante 3, con un descuadre de contadores en vez de la causa.
    for (Future<?> future : futures) {
      future.get();
    }

    int finalStock = stockService.currentStock(productId);

    // Invariant 1: stock must never go negative
    assertThat(finalStock).as("Stock must never be negative").isGreaterThanOrEqualTo(0);

    // Invariant 2: stock + successCount must equal INITIAL_STOCK (no lost updates)
    assertThat(finalStock + successCount.get())
        .as("finalStock + successCount must equal INITIAL_STOCK — no lost updates")
        .isEqualTo(INITIAL_STOCK);

    // Invariant 3: total operations = success + fail
    assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
  }

  @Test
  @DisplayName("5 concurrent IN(2) — stock increases correctly without lost updates")
  void concurrentInRequests_stockIncreasesCorrectly() throws InterruptedException {
    int threadCount = 5;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              stockService.registerMovement(
                  new StockMovementRequest(
                      productId, MovementType.IN, 2, "concurrent-in-test", null),
                  AUTH);
              successCount.incrementAndGet();
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();

    int finalStock = stockService.currentStock(productId);
    int expectedStock = INITIAL_STOCK + (successCount.get() * 2);

    // Invariant: every IN must be reflected — no lost updates
    assertThat(finalStock)
        .as("finalStock must equal INITIAL_STOCK + (successCount * 2) — no lost IN updates")
        .isEqualTo(expectedStock);
  }
}
