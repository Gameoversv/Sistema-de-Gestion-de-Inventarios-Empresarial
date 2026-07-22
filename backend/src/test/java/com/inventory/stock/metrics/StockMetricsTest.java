package com.inventory.stock.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement.MovementType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockMetrics")
class StockMetricsTest {

  @Mock private ProductRepository productRepository;

  private MeterRegistry registry;
  private StockMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new StockMetrics(registry, productRepository);
  }

  @Test
  @DisplayName("cuenta los movimientos por tipo en series separadas")
  void countsMovementsByType() {
    metrics.recordMovement(MovementType.OUT, 3);
    metrics.recordMovement(MovementType.OUT, 7);
    metrics.recordMovement(MovementType.IN, 20);

    assertThat(movementCount(MovementType.OUT)).isEqualTo(2);
    assertThat(movementCount(MovementType.IN)).isEqualTo(1);
    assertThat(registry.find(StockMetrics.MOVEMENTS).tag("type", "ADJUSTMENT").counter()).isNull();
  }

  @Test
  @DisplayName("acumula las unidades movidas, no solo el número de movimientos")
  void accumulatesUnitsMoved() {
    metrics.recordMovement(MovementType.OUT, 3);
    metrics.recordMovement(MovementType.OUT, 7);

    assertThat(registry.find(StockMetrics.UNITS).tag("type", "OUT").counter().count())
        .isEqualTo(10.0);
  }

  @Test
  @DisplayName("cuenta las alertas de umbral por SKU")
  void countsThresholdAlertsBySku() {
    metrics.recordThresholdAlert("SKU-001");
    metrics.recordThresholdAlert("SKU-001");
    metrics.recordThresholdAlert("SKU-002");

    assertThat(registry.find(StockMetrics.ALERTS).tag("sku", "SKU-001").counter().count())
        .isEqualTo(2.0);
    assertThat(registry.find(StockMetrics.ALERTS).tag("sku", "SKU-002").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("el gauge refleja los productos bajo mínimo")
  void gaugeReportsProductsBelowMinimum() {
    when(productRepository.countLowStockProducts()).thenReturn(4L);

    assertThat(registry.find(StockMetrics.BELOW_MINIMUM).gauge().value()).isEqualTo(4.0);
  }

  @Test
  @DisplayName("si la consulta del gauge falla, devuelve el último valor conocido")
  void gaugeFallsBackToLastKnownValueOnFailure() {
    when(productRepository.countLowStockProducts())
        .thenReturn(6L)
        .thenThrow(new IllegalStateException("base de datos caída"));

    assertThat(registry.find(StockMetrics.BELOW_MINIMUM).gauge().value()).isEqualTo(6.0);

    // Segundo scrape con la consulta fallando: el endpoint de Prometheus no puede romperse.
    assertThat(registry.find(StockMetrics.BELOW_MINIMUM).gauge().value()).isEqualTo(6.0);
  }

  private double movementCount(MovementType type) {
    return registry.find(StockMetrics.MOVEMENTS).tag("type", type.name()).counter().count();
  }
}
