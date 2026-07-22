package com.inventory.stock.metrics;

import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement.MovementType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Métricas de negocio del inventario. Los paneles del dashboard de Negocio medían hasta ahora tasas
 * de peticiones HTTP por ruta, que no distinguen un listado de un movimiento ni un POST correcto de
 * uno que devolvió 403. Estas tres familias sí responden preguntas de negocio: cuántos movimientos
 * hubo y de qué tipo, cuántas unidades entraron o salieron, cuántas veces se cruzó un mínimo y
 * cuántos productos están bajo mínimo ahora mismo.
 */
@Component
@Slf4j
public class StockMetrics {

  static final String MOVEMENTS = "inventory.stock.movements";
  static final String UNITS = "inventory.stock.units";
  static final String ALERTS = "inventory.stock.alerts";
  static final String BELOW_MINIMUM = "inventory.products.below_minimum";

  private final MeterRegistry registry;
  private final ProductRepository productRepository;

  // Último valor conocido del gauge. Si la consulta falla durante un scrape se devuelve este en
  // lugar de propagar la excepción: una base de datos caída no puede además tumbar
  // /actuator/prometheus, que es justo lo que se consulta para diagnosticarla.
  private volatile double lastKnownBelowMinimum;

  public StockMetrics(MeterRegistry registry, ProductRepository productRepository) {
    this.registry = registry;
    this.productRepository = productRepository;

    Gauge.builder(BELOW_MINIMUM, this::countBelowMinimum)
        .description("Productos activos cuyo stock está en el mínimo o por debajo")
        .register(registry);
  }

  /** Un movimiento confirmado, desglosado por tipo: IN, OUT o ADJUSTMENT. */
  public void recordMovement(MovementType type, int quantity) {
    registry.counter(MOVEMENTS, "type", type.name()).increment();
    registry.counter(UNITS, "type", type.name()).increment(quantity);
  }

  /**
   * Un producto que acaba de cruzar su mínimo.
   *
   * <p>El SKU va como etiqueta —una serie por producto— porque la pregunta que hay que poder
   * responder es <em>qué</em> productos cruzan el mínimo, no solo cuántas veces. Con un catálogo de
   * cientos de referencias la cardinalidad es asumible; con decenas de miles habría que agregarlo
   * por categoría.
   */
  public void recordThresholdAlert(String sku) {
    registry.counter(ALERTS, "sku", sku).increment();
  }

  private double countBelowMinimum() {
    try {
      lastKnownBelowMinimum = productRepository.countLowStockProducts();
    } catch (RuntimeException ex) {
      log.warn("No se pudo calcular inventory_products_below_minimum: {}", ex.getMessage());
    }
    return lastKnownBelowMinimum;
  }
}
