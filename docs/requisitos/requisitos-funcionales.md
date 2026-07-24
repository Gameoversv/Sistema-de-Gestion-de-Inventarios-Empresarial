# Requisitos Funcionales

Qué hace el sistema. Cada fila cita el enunciado, apunta al código que la implementa y a la prueba que la verifica. Criterios de estado y convenciones en el [README](README.md).

> **Aviso sobre las rutas.** El sistema expone dos prefijos distintos: `/products` y `/categories` sin prefijo, y `/api/stock`, `/api/reports` y `/api/audit` con él. Es una inconsistencia real, no un error de este documento. Unificar bajo `/api/v1` es **A-1** en el plan de ejecución, descartado cerca de la entrega por riesgo. Las rutas que aparecen abajo son las que responden de verdad.

---

## 1. Gestión de Productos

> *"Agregar Producto: … incluyendo detalles como: Nombre, Código SKU, Descripción, Categoría, Precio, Cantidad inicial, Stock mínimo y Estado (activo/inactivo)"*

### RF-01 — Alta de producto

| | |
|---|---|
| **Descripción** | Un usuario con `product:manage` crea un producto con los ocho atributos exigidos. |
| **Origen** | Enunciado, Alcance Funcional 1.a |
| **Estado** | **Cumple** |
| **Implementación** | `POST /products` — [`ProductController.java:83`](../../backend/src/main/java/com/inventory/product/web/ProductController.java#L83); entidad en [`Product.java`](../../backend/src/main/java/com/inventory/product/domain/Product.java) |
| **Verificación** | `ProductControllerTest`, `ProductCreateRequestValidationTest`, `ProductServiceTest` |

Los ocho atributos del enunciado están los ocho, con sus restricciones:

| Atributo PDF | Campo | Restricción |
|---|---|---|
| Nombre | `name` | obligatorio, ≤ 255 |
| Código SKU | `sku` | obligatorio, ≤ 100, **único en base de datos** |
| Descripción | `description` | opcional, `TEXT` |
| Categoría | `category` | `@ManyToOne` opcional hacia `Category` |
| Precio | `price` | obligatorio, ≥ 0.00, `NUMERIC(15,2)` |
| Cantidad inicial | `stock` | obligatorio, ≥ 0 |
| Stock mínimo | `minimumStock` | ≥ 0, no nulo en columna |
| Estado (activo/inactivo) | `active` | no nulo |

`price` usa `BigDecimal` con escala 2, no `double`: el dinero no se representa en coma flotante binaria.

### RF-02 — Edición de producto

| | |
|---|---|
| **Descripción** | Edición total (`PUT`) y parcial (`PATCH`) de un producto existente. |
| **Origen** | Alcance Funcional 1.b |
| **Estado** | **Cumple** |
| **Implementación** | [`ProductController.java:125`](../../backend/src/main/java/com/inventory/product/web/ProductController.java#L125) y [`:148`](../../backend/src/main/java/com/inventory/product/web/ProductController.java#L148) |
| **Verificación** | `ProductControllerTest`, `ProductServiceImplExtendedTest` |

### RF-03 — Eliminación de producto

| | |
|---|---|
| **Descripción** | `DELETE` sobre un producto lo retira del inventario operativo. |
| **Origen** | Alcance Funcional 1.c |
| **Estado** | **Cumple** |
| **Implementación** | [`ProductController.java:171`](../../backend/src/main/java/com/inventory/product/web/ProductController.java#L171) |
| **Verificación** | `ProductControllerTest`, `ProductServiceTest` |

**Es soft delete**, no borrado físico: la operación pone `active = false` y el producto desaparece de los listados por defecto. La razón es que un producto con movimientos de stock históricos no puede desaparecer sin romper la trazabilidad que exigen RF-11 y RF-12. Queda por escribir el ADR que lo justifique formalmente — **F-1 / ADR-003** en el plan.

### RF-04 — Listado paginado

| | |
|---|---|
| **Descripción** | Lista de productos paginada, con tamaño de página configurable. |
| **Origen** | Alcance Funcional 1.d — *"Paginación"* |
| **Estado** | **Cumple** |
| **Implementación** | [`ProductController.java:50`](../../backend/src/main/java/com/inventory/product/web/ProductController.java#L50) — `@PageableDefault(size = 20, sort = "name")`, devuelve `Page<ProductResponse>` |
| **Verificación** | `ProductControllerTest`, `ProductRepositoryIT` |

### RF-05 — Búsqueda

| | |
|---|---|
| **Descripción** | Parámetro `search` que busca **por nombre o por SKU** en la misma consulta. |
| **Origen** | Alcance Funcional 1.d — *"Búsqueda"* |
| **Estado** | **Cumple** |
| **Implementación** | `GET /products?search=` — `ProductController.java:50`, resuelto con Specification en `ProductRepositoryIT` |
| **Verificación** | `ProductRepositoryIT`, `ProductServiceTest` |

### RF-06 — Filtros

| | |
|---|---|
| **Descripción** | Filtrado por categoría (`categoryId`) y por estado activo/inactivo (`active`), combinables con la búsqueda. |
| **Origen** | Alcance Funcional 1.d — *"Filtros"* |
| **Estado** | **Cumple** |
| **Implementación** | `GET /products?categoryId=&active=` — `ProductController.java:50` |
| **Verificación** | `ProductRepositoryIT`, `ProductServiceImplExtendedTest` |

### RF-07 — Ordenamiento

| | |
|---|---|
| **Descripción** | Ordenación por SKU, nombre, precio y stock, ascendente y descendente, desde la propia tabla. |
| **Origen** | Alcance Funcional 1.d — *"Ordenamiento"* |
| **Estado** | **Cumple** |
| **Implementación** | `GET /products?sort=campo,dir`; en el frontend, cabeceras con `aria-sort` |
| **Verificación** | `ProductControllerTest`; [informe F-2](../testing/reportes/F-2-D-1-D-2-alcance-funcional.md) |

El backend ya aceptaba `sort`, pero el hook del frontend lo tenía fijado a `name`, así que la capacidad existía y no era alcanzable. Al conectarlo se destapó que un campo de ordenación inválido devolvía **500**; ahora devuelve **400**.

### RF-08 — Gestión de categorías · [criterio propio]

| | |
|---|---|
| **Descripción** | CRUD de categorías para dar contenido al atributo Categoría de RF-01. |
| **Origen** | **[criterio propio]** — el enunciado exige el atributo Categoría, no un módulo para administrarlo |
| **Estado** | **Cumple** |
| **Implementación** | [`CategoryController.java`](../../backend/src/main/java/com/inventory/product/web/CategoryController.java) — `GET /categories` (`:45`), `GET /categories/{id}` (`:53`), `POST` (`:71`), `PUT` (`:90`), `DELETE` (`:113`) |
| **Verificación** | `CategoryControllerTest`, `CategoryServiceTest`, `CategoryCreateRequestValidationTest` |

---

## 2. Control de Stock

### RF-09 — Registro de movimientos

| | |
|---|---|
| **Descripción** | Actualización de existencias por entrada, salida y ajuste. El movimiento es la única vía de cambiar el stock: no se edita el campo a mano. |
| **Origen** | Alcance Funcional 2.a — *"entrada y salida de productos"* |
| **Estado** | **Cumple** |
| **Implementación** | `POST /api/stock/movements` — [`StockController.java:54`](../../backend/src/main/java/com/inventory/stock/web/StockController.java#L54); tipos `IN`, `OUT`, `ADJUSTMENT` en [`StockMovement.java`](../../backend/src/main/java/com/inventory/stock/domain/StockMovement.java) |
| **Verificación** | `StockControllerTest`, `StockServiceTest`, **`StockServiceConcurrencyIT`** |

`StockServiceConcurrencyIT` cubre lo que un test unitario no ve: dos movimientos simultáneos sobre el mismo producto no pueden dejar el stock inconsistente.

### RF-10 — Alertas por stock mínimo

| | |
|---|---|
| **Descripción** | El sistema señala los productos que alcanzan o bajan de su `minimumStock`. |
| **Origen** | Alcance Funcional 2.b |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/stock/alerts` ([`StockController.java:117`](../../backend/src/main/java/com/inventory/stock/web/StockController.java#L117)), `GET /api/reports/low-stock` (`:63`) y `critical-stock` (`:86`) |
| **Verificación** | `StockControllerTest`, `ReportServiceTest`, `StockMetricsTest` |

La alerta existe en tres planos, y es deliberado: la **API** para la interfaz, un **contador Prometheus** para el dashboard de Negocio y una **regla de Alertmanager** (`ProductosBajoMinimo`) para el aviso operativo. Ver [informe OBS-2/E-3](../testing/reportes/OBS-2-E-3-metricas-de-negocio.md).

### RF-11 — Historial de movimientos

| | |
|---|---|
| **Descripción** | Registro consultable de todas las entradas y salidas, con los seis campos exigidos. |
| **Origen** | Alcance Funcional 2.c — *"Fecha, Usuario, Tipo de movimiento, Cantidad anterior, Cantidad nueva y Observaciones"* |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/stock/movements` — [`StockController.java:100`](../../backend/src/main/java/com/inventory/stock/web/StockController.java#L100) |
| **Verificación** | `StockControllerTest`, `StockMovementSpecTest` |

Los seis campos, uno a uno:

| Campo PDF | Campo del sistema |
|---|---|
| Fecha | `createdAt` — [`BaseEntity.java:27`](../../backend/src/main/java/com/inventory/common/domain/BaseEntity.java#L27), poblado por el auditing de Spring Data |
| Usuario | `performedBy` |
| Tipo de movimiento | `type` — `IN` / `OUT` / `ADJUSTMENT` |
| Cantidad anterior | `quantityBefore` |
| Cantidad nueva | `quantityAfter` |
| Observaciones | `reason` |

Se añade `referenceId` **[criterio propio]**, para enganchar un movimiento a un documento externo.

### RF-12 — Auditoría con Hibernate Envers

| | |
|---|---|
| **Descripción** | Toda modificación de producto y de movimiento queda versionada con su revisión. |
| **Origen** | Alcance Funcional 2.d — *"Hibernate Envers O mecanismo equivalente"* |
| **Estado** | **Cumple** |
| **Implementación** | `@Audited` en `Product` y `StockMovement`; [`RevisionInfo.java`](../../backend/src/main/java/com/inventory/audit/domain/RevisionInfo.java); tablas `*_aud` creadas en [`V4__envers_audit_tables.sql`](../../backend/src/main/resources/db/migration/V4__envers_audit_tables.sql) |
| **Verificación** | `AuditIntegrationIT`, `StockAuditServiceTest`, `UnifiedAuditServiceTest` |

`store_data_at_delete: true` en la configuración de Envers: al eliminar, la revisión conserva los datos, no solo la marca de borrado.

### RF-13 — Consulta de auditoría

| | |
|---|---|
| **Descripción** | Consulta del rastro de auditoría, separada por dominio y unificada. |
| **Origen** | Derivado de 2.d: una auditoría que no se puede consultar no es auditoría |
| **Estado** | **Cumple** |
| **Implementación** | [`AuditController.java`](../../backend/src/main/java/com/inventory/audit/web/AuditController.java) — `/api/audit/stock-movements` (`:50`), `/api/audit/products` (`:73`), `/api/audit/all` (`:92`); todos exigen `audit:view` |
| **Verificación** | `AuditControllerTest`, `AuditIntegrationIT` |

---

## 3. API Empresarial

### RF-14 — API REST documentada

| | |
|---|---|
| **Descripción** | API REST con especificación OpenAPI y Swagger UI navegable. |
| **Origen** | Alcance Funcional 3.a — *"OpenAPI y Swagger UI"* |
| **Estado** | **Cumple** |
| **Implementación** | springdoc; `/swagger-ui.html` y `/v3/api-docs`, públicos por [`SecurityConfig.java:63-72`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L63); contrato volcado en [`docs/api/openapi.yaml`](../api/) |
| **Verificación** | El perfil `generate-docs` del POM consume `/v3/api-docs.yaml`; ZAP se siembra con ese mismo contrato |

Swagger UI está configurado con **PKCE** contra Keycloak (`use-pkce-with-authorization-code-grant: true`), así que el "Try it out" funciona con un token real en vez de pedir pegarlo a mano.

### RF-15 — Cobertura funcional de la API

| | |
|---|---|
| **Descripción** | La API permite CRUD de productos, consulta de inventario, movimientos de stock y reportes. |
| **Origen** | Alcance Funcional 3.b |
| **Estado** | **Cumple** |
| **Implementación** | Los cuatro bloques: `/products` + `/categories`, `/api/reports/stock-summary`, `/api/stock/movements`, `/api/reports/*` |
| **Verificación** | `ProductControllerTest`, `StockControllerTest`, `ReportControllerTest`, `ReportControllerExtendedTest` |

No existe `GET /api/stock/{productId}`: la existencia de un producto concreto se consulta en su propio recurso. Añadir el atajo es **M-2**, mejora opcional del plan.

---

## 4. Interfaz de usuario y dashboard

> *"Debe incluir: Productos críticos, Productos más vendidos, Historial reciente, Métricas del sistema y Indicadores operacionales"*

### RF-16 — Productos críticos

| | |
|---|---|
| **Origen** | Alcance Funcional 4.c |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/reports/critical-stock` — [`ReportController.java:86`](../../backend/src/main/java/com/inventory/report/web/ReportController.java#L86); el dashboard lista los productos, no solo su número |
| **Verificación** | `ReportServiceTest`; [informe D-2](../testing/reportes/F-2-D-1-D-2-alcance-funcional.md) |

Antes mostraba únicamente el contador. Al listarlos se descubrió que el tipo TypeScript de `CriticalStockResponse` estaba mal y rompía las `key` de React.

### RF-17 — Productos más vendidos

| | |
|---|---|
| **Origen** | Alcance Funcional 4.c |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/reports/best-sellers` — [`ReportController.java:128`](../../backend/src/main/java/com/inventory/report/web/ReportController.java#L128), agregando movimientos `OUT` |
| **Verificación** | `BestSellersTest`; [informe D-1](../testing/reportes/F-2-D-1-D-2-alcance-funcional.md) |

El panel anterior ordenaba por precio × stock, que mide **lo guardado, no lo vendido**. Un producto caro que no sale de almacén encabezaba la lista de "más vendidos". Se sustituyó por una agregación real sobre las salidas.

### RF-18 — Historial reciente

| | |
|---|---|
| **Origen** | Alcance Funcional 4.c |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/reports/recent-movements` — [`ReportController.java:168`](../../backend/src/main/java/com/inventory/report/web/ReportController.java#L168) |
| **Verificación** | `ReportControllerExtendedTest` |

### RF-19 — Métricas del sistema

| | |
|---|---|
| **Origen** | Alcance Funcional 4.c |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/reports/dashboard-metrics` (`:150`) y `stock-summary` (`:49`) |
| **Verificación** | `ReportServiceExtendedTest`, `ReportControllerTest` |

### RF-20 — Indicadores operacionales

| | |
|---|---|
| **Origen** | Alcance Funcional 4.c |
| **Estado** | **Cumple** |
| **Implementación** | `GET /api/reports/top-products` (`:102`) más los cuatro dashboards de Grafana |
| **Verificación** | [informe de dashboards](../testing/reportes/OBS-dashboards.md) — 37 consultas, ningún panel vacío |

### RF-21 — Navegación y usabilidad

| | |
|---|---|
| **Descripción** | *"interfaz intuitiva y fácil de usar, con una navegación clara y accesible"*. |
| **Origen** | Alcance Funcional 4.b |
| **Estado** | **Parcial** |
| **Implementación** | Cinco vistas bajo un `Layout` común — [`App.tsx:26-30`](../../frontend/src/App.tsx#L26): dashboard (`/`), `products`, `stock`, `reports`, `audit`. Los controles de ordenación exponen `aria-sort`; los elementos que el usuario no puede usar se ocultan con `PermissionGuard` en vez de fallar con un 403 |
| **Verificación** | `PermissionGuard.test.tsx`, `Badge.test.tsx`; E2E en `e2e/tests/` (`auth`, `products`, `stock`) |
| **Qué falta** | La accesibilidad no se comprueba de forma automatizada: **D-4** añade `@axe-core/playwright`. Y los tres specs de Playwright existen pero **no los ejecuta el pipeline** (**C-1 / TEST-7**), así que hoy no hay verificación continua de la navegación |

---

## 5. Modelo de permisos

> *"No se permitirá validar acceso únicamente por nombre de rol; cada operación crítica deberá verificar el permiso correspondiente."*

### RF-22 — Matriz de permisos aplicada por endpoint

| | |
|---|---|
| **Descripción** | Los siete permisos de la matriz mínima existen y **protegen endpoints concretos**, comprobando el permiso y no el nombre del rol. |
| **Origen** | Roles y Niveles de Acceso — Matriz mínima de permisos |
| **Estado** | **Parcial** |
| **Implementación** | `@PreAuthorize("hasAuthority('SCOPE_…')")` en los cuatro controladores de negocio |
| **Verificación** | `SecurityIntegrationTest`, `KeycloakJwtConverterTest`, `AuditControllerTest` |
| **Qué falta** | **`user:manage` no protege ningún endpoint** (**A-2**, issue #48): el permiso se emite y se reconoce, pero la gestión de usuarios se delega hoy en la consola de Keycloak. Seis de siete permisos están aplicados |

| Permiso | Módulo | Dónde se exige |
|---|---|---|
| `product:view` | Productos | lectura en `/products` y `/categories` |
| `product:manage` | Productos | alta, edición y borrado en `/products` y `/categories` |
| `stock:view` | Stock | `GET /api/stock/movements`, `GET /api/stock/alerts` |
| `stock:manage` | Stock | `POST /api/stock/movements` |
| `report:view` | Reportes | los siete endpoints de `/api/reports` |
| `audit:view` | Auditoría | los tres endpoints de `/api/audit` |
| `user:manage` | Seguridad | **ningún endpoint** — A-2 |

Los roles se construyen como combinaciones de permisos, según exige el enunciado. La tabla vive en [`SecurityConfig.java:177`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L177):

| Rol de realm | Permisos |
|---|---|
| `inventory-admin` | los siete |
| `warehouse-clerk` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
| `auditor` | `product:view`, `stock:view`, `report:view`, `audit:view` |
| `viewer` | `product:view`, `stock:view`, `report:view` |

Un usuario con varios roles recibe la **unión** de sus permisos, y quien no tiene ningún rol reconocido no recibe **ninguno**: se deniega por defecto ([`SecurityConfig.java:195`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L195)). Por qué esta tabla vive en Java y no en Keycloak, y qué implica, está en [RNF-02](requisitos-no-funcionales.md#rnf-02--autorización-por-permiso-no-por-rol).
