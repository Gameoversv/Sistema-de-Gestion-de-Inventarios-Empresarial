# OBS-2 / E-3 — Métricas de negocio del inventario

**Fecha:** 2026-07-22
**Entorno:** stack local en `feat/obs-4-loki-logs`, backend Spring Boot 3.3.5 (perfil `dev`), Prometheus, Alertmanager
**Estado:** verificado en vivo

---

## Resumen

Antes de este cambio, `grep` de `MeterRegistry`, `Counter`, `Timer`, `@Timed` y `Gauge` en todo `src/main/java` devolvía **cero resultados**. Los paneles de "Métricas de Negocio" eran en realidad tasas de peticiones HTTP por ruta: un 403, un listado y un POST fallido contaban igual que un movimiento de stock real.

Ahora hay cuatro series que sí responden preguntas de negocio, y la alerta de stock —el evento más relevante del sistema, que hasta ahora solo existía como un `log.warn`— llega a Prometheus y a Alertmanager.

---

## Métricas añadidas

| Serie | Tipo | Etiquetas | Pregunta que responde |
|---|---|---|---|
| `inventory_stock_movements_total` | Counter | `type` | ¿Cuántos movimientos hubo, y de qué tipo? |
| `inventory_stock_units_total` | Counter | `type` | ¿Cuántas unidades entraron o salieron? |
| `inventory_stock_alerts_total` | Counter | `sku` | ¿Qué productos cruzaron el mínimo, y cuántas veces? |
| `inventory_products_below_minimum` | Gauge | — | ¿Cuántos productos están bajo mínimo **ahora**? |

### Decisiones de diseño

**Los contadores se incrementan tras el commit, no dentro de la transacción.** `StockServiceImpl` publica `StockMovementRecordedEvent` y `StockMovementMetricsListener` lo consume con `@TransactionalEventListener(AFTER_COMMIT)`, igual que ya hacía `StockThresholdListener`. Un movimiento cuya transacción se deshace no queda contado en el dashboard.

**El SKU va como etiqueta en las alertas.** La pregunta que hay que poder responder es *qué* productos cruzan el mínimo, no solo cuántas veces. Con un catálogo de cientos de referencias la cardinalidad es asumible; con decenas de miles habría que agregar por categoría. Queda anotado como límite conocido.

**El gauge no puede tumbar `/actuator/prometheus`.** Se evalúa en cada scrape con una consulta `COUNT` —`ProductRepository.countLowStockProducts()`, sin materializar entidades—. Si la consulta falla, devuelve el último valor conocido y registra un `warn` en lugar de propagar la excepción: una base de datos caída no puede además dejar sin métricas al endpoint que se consulta para diagnosticarla. Cubierto por test.

---

## Alerta de negocio

Añadida al grupo `negocio` de `observability/prometheus/rules/alerts.yml`, **más allá de las cinco obligatorias**, que siguen intactas:

```yaml
- alert: ProductosBajoMinimo
  expr: inventory_products_below_minimum > 0
  for: 10m
```

`promtool check rules` → `SUCCESS: 6 rules found`.

---

## Verificación

### 1. El gauge existe desde el arranque, sin tráfico

```
inventory_products_below_minimum{application="inventory-api",profile="dev"} 0.0
```

### 2. Cuatro movimientos reales por la API

```
POST /api/stock/movements  ×4  → 201 201 201 201
  SK1              OUT 2  (10 → 8)
  SK1              OUT 4  (8  → 4)   cruza el mínimo (5)
  SK1              IN  10 (4  → 14)  repone
  SKU-MULTIROL-01  OUT 4  (5  → 1)   cruza el mínimo (1)
```

Resultado en `/actuator/prometheus`:

```
inventory_products_below_minimum{...} 1.0
inventory_stock_alerts_total{...,sku="SK1"} 1.0
inventory_stock_alerts_total{...,sku="SKU-MULTIROL-01"} 1.0
inventory_stock_movements_total{...,type="IN"} 1.0
inventory_stock_movements_total{...,type="OUT"} 3.0
inventory_stock_units_total{...,type="IN"} 10.0
inventory_stock_units_total{...,type="OUT"} 10.0
```

Los cuatro contadores cuadran con los movimientos ejecutados. El gauge marca 1 porque `SK1` volvió a estar por encima del mínimo tras la reposición y solo queda `SKU-MULTIROL-01`: distingue el histórico —el contador de alertas, que no baja— de la situación actual.

### 3. Prometheus las scrapea

```
sum by (type) (inventory_stock_movements_total)
→ {type="IN"} 1, {type="OUT"} 3
```

### 4. La alerta evalúa con datos reales

```
GET /api/v1/alerts
→ alertname="ProductosBajoMinimo", componente="negocio", severity="warning"
  resumen: "1 productos en el mínimo o por debajo"
```

En estado `pending`: la expresión ya casa y `for: 10m` está corriendo. Las anotaciones y el runbook se resuelven correctamente con el valor real.

### 5. Pruebas unitarias

- `StockMetricsTest` (5): desglose por tipo, acumulación de unidades, alertas por SKU, gauge, y el fallback del gauge cuando la consulta falla.
- `StockEventListenersTest` (2): cada evento incrementa su contador.
- `StockServiceTest`: nuevo test de publicación de `StockMovementRecordedEvent`; los dos tests de eventos existentes se ajustaron, porque `registerMovement` ahora publica dos eventos distintos y `verify(eventPublisher)` daba por supuesto uno solo.

Suite completa: **284 tests, 0 fallos** (eran 276).

---

## Pendiente

Los paneles del dashboard de Negocio siguen midiendo tráfico HTTP. Las series ya existen; sustituirlos entra en la tarea de separar los 4 dashboards, que es lo siguiente de la Ola 2.
