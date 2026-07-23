# Análisis de Brechas — Proyecto Final QAS

**Guía evaluada:** `Proyecto_Final_V3.pdf` (Freddy Peña — PUCMM, Aseguramiento Calidad Software)
**Repositorio:** Sistema-de-Gestión-de-Inventarios-Empresarial
**Commit de referencia:** `ff1ab24`
**Fecha del análisis:** 2026-07-21

---

## 1. Resumen ejecutivo

El proyecto tiene una base funcional y de testing sólida (backend Spring Boot completo, frontend React, Testcontainers, Playwright, Postman, ZAP, Jenkins y GitHub Actions con despliegue staging real). Las brechas se concentran en **Observabilidad** y en varias **capas de testing obligatorias**, que juntas suman **35% de la nota**.

### Estado por área de evaluación

| Área | Peso | Estado | Cumplimiento estimado |
|------|------|--------|----------------------|
| Funcionalidad | 15% | Casi completo — 1 elemento del dashboard ausente | ~85% |
| Testing | 20% | Parcial — faltan 3 de 8 capas | ~60% |
| Seguridad | 10% | Scopes bien aplicados, pero sin Policies y con 2 defectos de autorización | ~70% |
| Observabilidad | 15% | **Crítico — solo métricas** | ~30% |
| CI/CD | 15% | Estructura buena, pero con etapas que no hacen lo que declaran | ~60% |
| Calidad de código | 10% | Sonar nunca ejecutado; Spotless evadido; frontend sin CI | ~35% |
| Documentación | 10% | **Crítico — falta casi todo** | ~25% |
| Presentación final | 5% | Pendiente | — |

### Top 5 brechas por impacto

1. **Observabilidad incompleta** — faltan Tempo, Loki, Alloy, Alertmanager y OpenTelemetry (15% de la nota, obligatorio explícito).
2. **Documentación ausente** — no existe documento de requisitos, documentación técnica con diagramas, ni guía de pruebas (10%).
3. **Performance testing inexistente** — no hay k6 ni JMeter en el repositorio (capa obligatoria).
4. **E2E no se ejecuta en el pipeline** — Playwright existe localmente pero ningún workflow lo invoca.
5. **SonarQube/SonarCloud no se ejecuta** — plugin configurado en `pom.xml` pero ningún job de CI lo llama (10%).

### Brechas funcionales concretas (baratas de cerrar, alta visibilidad)

Detectadas al verificar comportamiento, no presencia de archivos. Todas son de bajo esfuerzo y se notan de inmediato en una demo:

- **D-1** — "Productos más vendidos" no existe; el dashboard muestra valor de inventario en su lugar.
- **A-1** — rutas de API inconsistentes (`/products` vs `/api/stock`) y sin versionar; los ejemplos `curl` del README apuntan a rutas inexistentes.
- **A-2** — el permiso `user:manage` de la matriz obligatoria no tiene ningún endpoint.
- **F-2** — ordenamiento de productos implementado en el backend pero no expuesto en la UI.
- **F-1** — `DELETE` de producto es soft delete sin justificación documentada.

---

## 2. Alcance funcional

### 2.1 Gestión de productos — **CASI COMPLETO (2 brechas)**

Implementado en `backend/src/main/java/com/inventory/product/`.

| Sub-requisito | Estado | Evidencia |
|---|---|---|
| a. Agregar producto (8 campos) | Cumple | `ProductCreateRequest` tiene sku, name, description, price, stock, minimumStock, active, categoryId — validados. SKU único → 409 (`ProductServiceImpl:39`) |
| b. Editar producto | Cumple | `PUT /products/{id}` + `PATCH /products/{id}` |
| c. Eliminar producto | **Parcial** | Ver F-1 |
| d. Visualizar — paginación | Cumple | `@PageableDefault(size=20)`; controles prev/next en `ProductsPage.tsx` |
| d. Visualizar — búsqueda | Cumple | `?search=` por nombre/SKU; input en UI |
| d. Visualizar — filtros | Cumple | `?categoryId=`, `?active=`; dos selects en UI |
| d. Visualizar — **ordenamiento** | **Parcial** | Ver F-2 |

**F-1 · `DELETE` es soft delete, sin justificar.**
`ProductServiceImpl:120-127` hace `product.setActive(false)` en vez de borrar. La guía pide "permitir la eliminación de productos del inventario". La decisión es técnicamente correcta —`StockMovement` tiene FK al producto y Envers audita el historial, un borrado físico rompería la trazabilidad— pero no está documentada en ninguna parte. Un evaluador que ejecute `DELETE` y consulte la base de datos lo leerá como requisito incumplido.
**Acción:** escribir `ADR-002-soft-delete-productos.md` con la justificación; renombrar la operación a "Desactivar producto" en la descripción de Swagger y en la UI para que el comportamiento sea explícito.

**F-2 · El ordenamiento existe en el backend pero la UI no lo expone.**
`useProducts.ts:24` fija `params.set('sort', 'name')` de forma permanente. Los `<th>` de la tabla (`ProductsPage.tsx:98-105`) son texto plano: sin click, sin indicador de dirección. El backend acepta cualquier campo vía `Pageable`, pero el frontend nunca lo aprovecha.
**Acción:** headers clicables para SKU, nombre, precio y stock, con estado `sort` + `direction` propagado al hook. Cambio pequeño (~30 líneas) sobre un sub-requisito explícito de la guía.

**F-3 · `categoryId` no es obligatorio (menor).**
No lleva `@NotNull` y `create()` guarda el producto sin categoría si llega nulo (`ProductServiceImpl:45`). La guía lista Categoría entre los detalles del producto. Decidir si es obligatoria y, en tal caso, validarlo.

### 2.2 Control de stock — **COMPLETO** (módulo más sólido del proyecto)

| Sub-requisito | Estado | Evidencia |
|---|---|---|
| a. Actualizar stock (entrada/salida) | Cumple con margen | `POST /api/stock/movements` con `IN`/`OUT`/`ADJUSTMENT`; bloqueo `PESSIMISTIC_WRITE` vía `findByIdForUpdate` contra condiciones de carrera (probado en `StockServiceConcurrencyIT`); `OUT` que dejaría stock negativo lanza `BusinessException` |
| b. Alertas por stock mínimo | Cumple | `GET /api/stock/alerts` (`stock <= minimumStock AND active`); `AlertsPanel` en `StockPage.tsx:185`; contadores `lowStockCount`/`criticalStockCount` en el dashboard; `StockThresholdCrossedEvent` publicado `AFTER_COMMIT` |
| c. Historial de movimientos (6 campos) | Cumple | `StockMovementResponse`: `createdAt`, `performedBy`, `type`, `quantityBefore`, `quantityAfter`, `reason`. Filtros por producto/tipo/rango de fechas + exportación CSV |
| d. Auditoría Hibernate Envers | Cumple | `@Audited` en `Product`, `Category`, `StockMovement`, `AppUser`; `EnversRevisionListener` graba el `preferred_username` del JWT en `RevisionInfo.username`; expuesto por `AuditController` con `SCOPE_audit:view` |

**E-1 · Columnas de snapshot nullable (menor).**
`V7__add_stock_movement_snapshots.sql` añadió `quantity_before`, `quantity_after` y `performed_by` sin `NOT NULL`. Los movimientos anteriores a V7 tienen NULL en los tres campos, y el export CSV los imprime literalmente como `"null"` (`StockPage.tsx:223`). La guía exige esos campos en todos los registros del historial.
**Acción:** migración V8 con backfill + `SET NOT NULL`.

**E-2 · Semántica sobrecargada de `quantity` (menor).**
En `IN`/`OUT` representa unidades del movimiento; en `ADJUSTMENT` representa el stock absoluto resultante (`computeNewStock`). Está documentado en Swagger pero se presta a confusión. Además `@Min(0)` permite `quantity = 0` en `IN`/`OUT`, generando movimientos no-op que ensucian el historial.
**Acción:** validación condicional por tipo, o separar el endpoint de ajuste.

**E-3 · La alerta no emite métrica (enganche con §6).**
`StockThresholdListener` solo hace `log.warn("STOCK ALERT ...")`. No incrementa ningún contador de Micrometer, así que el evento de negocio más relevante del sistema no llega ni al dashboard de Negocio ni a Alertmanager.
**Acción:** añadir `Counter` `inventory_stock_alerts_total{sku}`. Cambio de una línea que convierte esto en evidencia directa de observabilidad.

### 2.3 API empresarial — **CASI COMPLETO (2 brechas)**

| Sub-requisito | Estado | Evidencia |
|---|---|---|
| a. REST documentada con OpenAPI + Swagger UI | Cumple con margen | `OpenApiConfig`: esquema OAuth2 Authorization Code + PKCE contra Keycloak, scopes documentados, 4 grupos (Productos, Stock, Reportes, Auditoría) |
| b. CRUD de productos | Cumple | `ProductController` + `CategoryController` |
| b. Consulta de inventario | Cumple | `GET /products` con filtros; `GET /api/stock/{productId}` |
| b. Movimientos de stock | Cumple | `GET`/`POST /api/stock/movements` |
| b. Reportes | Cumple | 6 endpoints en `ReportController` |

**A-1 · Rutas inconsistentes y sin versionar.**
Conviven dos convenciones sin ningún `context-path` que las unifique:

| Sin prefijo | Con prefijo `/api` |
|---|---|
| `/products`, `/categories`, `/me`, `/ping`, `/health` | `/api/stock`, `/api/reports`, `/api/audit` |

No hay versionado (`/v1`) en ninguna. Peor: el README documenta la base URL como `http://localhost:8080/api/v1` y lista `GET /api/v1/health` — **esa ruta no existe**; los ejemplos con `curl` del README fallan tal cual están escritos. Para un requisito titulado "API Empresarial" esto es lo primero que nota un evaluador.
**Acción:** unificar bajo `/api/v1` con `server.servlet.context-path` o normalizando los `@RequestMapping`, actualizar `OpenApiConfig.pathsToMatch`, la colección Postman, el frontend (`api.ts`) y los smoke tests de ambos pipelines. Corregir el README.

**A-2 · El permiso `user:manage` no tiene implementación.**
Es uno de los 7 permisos de la matriz obligatoria ("Gestionar usuarios, roles y permisos"). Está definido como client scope en Keycloak y se solicita en los tests de staging, pero **no existe ningún controller de usuarios**: `security/` tiene `AppUser`, `User`, `AppUserRepository` y `UserRepository`, sin capa `web/`. Ningún `@PreAuthorize` referencia `SCOPE_user:manage`. Tampoco aparece en la lista de scopes de `OpenApiConfig` (están los otros 6).
**Acción:** decidir entre implementar un `UserController` mínimo (listar usuarios, ver/asignar roles vía Admin API de Keycloak) o justificar por escrito que la gestión se delega a la consola de Keycloak. La primera opción defiende mejor el 10% de Seguridad; la segunda al menos evita que se lea como requisito olvidado.

**A-3 · Contacto personal en OpenAPI (cosmético).**
`OpenApiConfig` expone `snipervargas37@gmail.com` como contacto de la API. Mejor un correo de equipo o institucional.

### 2.4 Interfaz de usuario — **BRECHA REAL (falta 1 de 5 elementos)**

| Elemento exigido | Estado | Evidencia |
|---|---|---|
| Productos críticos | **Parcial** | Solo el contador `criticalStockCount` en un KPI. El endpoint `/api/reports/critical-stock` existe pero el dashboard no lo consume — no se listan los productos |
| **Productos más vendidos** | **NO IMPLEMENTADO** | Ver D-1 |
| Historial reciente | Cumple | Panel "Movimientos recientes" con producto, tipo, cantidad y usuario |
| Métricas del sistema | Cumple (interpretación de negocio) | `DashboardMetricsResponse`: totales, valor de inventario, contadores de alerta, último movimiento |
| Indicadores operacionales | Cumple | 4 KPIs + gráfico de barras (Recharts) |

**D-1 · "Productos más vendidos" no existe — se sustituyó por "mayor valor de inventario".**
`ReportServiceImpl.topProducts` solo ordena por dos criterios:
- `metric=value` → `precio × stock` (valor inmovilizado)
- `metric=quantity` → `stock` actual

Ninguno mide ventas. Un producto con stock alto y sin rotación encabeza el ranking; el más vendido puede no aparecer. El dashboard llama `metric=value` y rotula el panel "Top 8 — valor de inventario" — honesto, pero no es lo que pide la guía.
El dato **sí existe**: `stock_movements` con `type = OUT` es exactamente el registro de salidas. Falta la agregación.
**Acción:** añadir `metric=sold` con una query tipo
`SELECT m.product, SUM(m.quantity) FROM StockMovement m WHERE m.type = 'OUT' AND m.createdAt >= :desde GROUP BY m.product ORDER BY SUM(m.quantity) DESC`,
exponerla en el dashboard como "Productos más vendidos" y conservar el gráfico de valor como panel adicional.

**D-2 · Productos críticos sin listado.**
Mostrar solo un número obliga al usuario a irse a otra pantalla para saber *qué* productos están en cero. El endpoint ya está hecho; es consumirlo y renderizar la lista.

**D-3 · `topProducts` carga toda la tabla en memoria (calidad/rendimiento).**
`productRepository.findAll()` seguido de `filter` + `sorted` en Java (`ReportServiceImpl:99-112`). No escala y SonarQube lo marcará. Debe resolverse con una query ordenada y paginada en base de datos.

**D-4 · Usabilidad y accesibilidad sin validar.**
Los objetivos de la guía incluyen "validar rendimiento, accesibilidad y experiencia de usuario". No hay ninguna verificación de accesibilidad en el repositorio: sin axe-core, sin Lighthouse, sin pruebas de navegación por teclado o contraste.
**Acción:** añadir `@axe-core/playwright` a la suite E2E (se integra con el trabajo pendiente de T-2) y una corrida de Lighthouse sobre el dashboard.

### 2.4 Interfaz de usuario — **BRECHA MENOR**

Existe `DashboardPage.tsx` con DTOs de respaldo (`CriticalStockResponse`, `TopProductsResponse`, `RecentMovementsResponse`, `DashboardMetricsResponse`).

| Requisito del dashboard | Estado |
|---|---|
| Productos críticos | Implementado |
| Productos más vendidos | Implementado |
| Historial reciente | Implementado |
| Métricas del sistema | Implementado |
| Indicadores operacionales | Verificar cobertura real en UI |

**Acción:** revisar `DashboardPage.tsx` contra los 5 puntos y capturar evidencia visual para la presentación.

---

## 3. Seguridad

### Cumplido

- Keycloak + OAuth2 + JWT (`SecurityConfig`), sesión stateless.
- **Modelo granular por scopes**: los 7 permisos de la matriz obligatoria (`product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view`, `user:manage`, `audit:view`) están definidos como optional client scopes del cliente `inventory-frontend` en `keycloak/realm-export.json`.
- `@PreAuthorize("hasAuthority('SCOPE_...')")` en todos los controllers — no se valida solo por nombre de rol.
- Intersección scope × rol en el converter JWT: un scope opcional no puede otorgar más de lo que el rol realm permite.
- Roles: `inventory-admin`, `warehouse-clerk`, `auditor`, `viewer`.
- CORS configurable por perfil; expiración de sesión (`accessTokenLifespan: 300`, `ssoSessionMaxLifespan: 36000`); `revokeRefreshToken` presente en el realm.
- Validación de 401/403 automatizada en Postman, GitHub Actions y Jenkins.

### Brechas

> **Corrección respecto a una versión anterior de este documento:** el hallazgo previo "S-1: el realm export tiene `clientScopes: []` y fallaría al importar" era **incorrecto**. `scripts/keycloak/init-users.sh` crea los 7 client scopes y los usuarios de prueba tras el import, ejecutado por el servicio `keycloak-init` de Compose. El provisionamiento es en dos etapas, no está roto.

### Cobertura de la matriz mínima de permisos

Verificado endpoint por endpoint: **23 endpoints de negocio, 23 con `@PreAuthorize`, cero usos de `hasRole`/`hasAnyRole` en el código de producción.** El requisito "no se permitirá validar acceso únicamente por nombre de rol; cada operación crítica deberá verificar el permiso correspondiente" **se cumple**.

| Módulo | Permiso | Endpoints que lo verifican | Estado |
|---|---|---|---|
| Productos | `product:view` | 4 — listar/ver productos, listar/ver categorías | Cumple |
| Productos | `product:manage` | 7 — crear/editar/PATCH/eliminar producto, crear/editar/eliminar categoría | Cumple |
| Stock | `stock:view` | 2 — historial de movimientos, alertas | Parcial (M-2) |
| Stock | `stock:manage` | 1 — registrar movimiento IN/OUT/ADJUSTMENT | Cumple |
| Reportes | `report:view` | 6 — todos los reportes y el dashboard | Cumple |
| Seguridad | `user:manage` | **0** | **NO IMPLEMENTADO** (M-1) |
| Auditoría | `audit:view` | 3 — auditoría de stock, de productos y unificada | Cumple |

**M-1 · El módulo Seguridad no tiene implementación.**
`user:manage` aparece únicamente en las dos listas hardcodeadas (`SecurityConfig.java:169`, `AuthContext.tsx:20`). Se declara en Keycloak y se emite en el token —verificado en un JWT real— pero no protege nada: no existen endpoints de gestión de usuarios, roles ni permisos. Es 1 de los 7 módulos obligatorios de la matriz. Amplía y confirma A-2.
**Acción:** implementar un `UserController` mínimo sobre la Admin API de Keycloak (listar usuarios, consultar y asignar roles) protegido con `SCOPE_user:manage`, o justificar por escrito la delegación a la consola de Keycloak.

**M-2 · `stock:view` cubre solo la mitad de su descripción.**
La matriz define el permiso como "Ver existencia **e historial**". El historial está cubierto por `GET /api/stock/movements`; la existencia no tiene endpoint propio — `StockService.currentStock()` existe pero no se expone, y el stock actual solo se obtiene vía `GET /products`, que exige `product:view`. Un usuario con únicamente `stock:view` no puede consultar existencias.
**Acción:** exponer `GET /api/stock/{productId}` protegido con `stock:view`.

**M-3 · Las combinaciones rol→permisos no viven en Keycloak.**
La matriz exige que "los roles del sistema deberán construirse asignando combinaciones de estos permisos". Las combinaciones son correctas pero están codificadas en `permittedScopesForRoles()`; en Keycloak los 4 roles son planos, sin relación con los scopes. Es la misma causa raíz que G-2, anotada aquí porque esta frase de la matriz es la que directamente la exige.

**Menores:** `ReportsPage.tsx` no usa `PermissionGuard` (el enlace del sidebar sí lo está y el backend valida igual). `PermissionGuard.test.tsx` solo cubre `product:view`; no hay pruebas por rol — enlaza con T-2.

### Evaluación del modelo granular (requisito: Roles, Permisos, Scopes y Policies)

| Elemento exigido | Estado |
|---|---|
| Keycloak | Implementado |
| OAuth2 | Implementado (Authorization Code + PKCE) |
| JWT | Implementado |
| Roles | Implementados (4 roles realm) |
| Permisos | Implementados (7 scopes de la matriz) |
| Scopes | Implementados y verificados en el token real |
| **Policies** | **NO IMPLEMENTADO** — ver G-1 |

| # | Brecha | Severidad | Acción |
|---|--------|-----------|--------|
| G-1 | **Keycloak Authorization Services no se usa.** Cero ocurrencias de `authorizationSettings`, `resources` o `policies` en el realm; `inventory-backend` es `bearerOnly` sin configuración de autorización. "Policies" es uno de los cinco elementos que la guía enumera explícitamente. | **Alta** | Definir Resources + Policies + Permissions en Keycloak, o justificar por escrito que el control por scopes los sustituye |
| G-2 | **El modelo de permisos está hardcodeado en Java.** `SecurityConfig.permittedScopesForRoles()` es un if/else. Cambiar lo que un rol puede hacer exige editar código y redesplegar — lo contrario a "cada funcionalidad deberá poder habilitarse o restringirse individualmente" y a un esquema "similar a ERP moderno". Keycloak queda reducido a proveedor de autenticación. | **Alta** | Mover la relación rol→permisos a Keycloak (roles compuestos, o Authorization Services) y que el backend solo consuma los scopes del token |
| G-3 | **Lógica duplicada en frontend.** `AuthContext.tsx:15` replica el if/else con el comentario `// Mirrors permittedScopesForRoles() in SecurityConfig.java — keep in sync`. Dos fuentes de verdad sincronizadas a mano. | Media | Eliminar la duplicación: el frontend debe leer los scopes del token, sin recalcular la política |
| G-4 | **Bug — los roles múltiples pierden permisos.** `permittedScopesForRoles` retorna en la primera coincidencia. Un usuario con `auditor` + `warehouse-clerk` entra por la rama de `warehouse-clerk` y pierde `audit:view`. Mismo defecto en el frontend. | **Alta** | Devolver la **unión** de los conjuntos de todos los roles del usuario. Añadir test unitario con usuario multi-rol |
| G-5 | **Fallback default-allow.** La rama final concede `product:view`, `stock:view` y `report:view` a cualquier rol no reconocido, incluido un usuario sin roles. Toda cuenta autenticada del realm obtiene lectura sin concesión explícita. | **Alta** | Devolver conjunto vacío por defecto (default-deny) y conceder solo por rol conocido |
| G-6 | **Riesgo de reproducibilidad.** El JWT extraído de una traza E2E contiene los 7 scopes, pero el repositorio los declara **opcionales** y ni `keycloak.ts` ni `AuthContext` solicitan `scope=` en `init()`/`login()`. Keycloak no emite scopes opcionales no solicitados: la configuración que generó ese token **no coincide con la del repositorio** y probablemente vive solo en el volumen de la base de datos de Keycloak. | **Alta** | Ejecutar `docker compose down -v && docker compose up` y comprobar el token. Si faltan scopes, corregir el código: declararlos como default client scopes, o solicitarlos en `login({ scope: ... })` |
| G-7 | **`realm-export.json` duplicado y divergente.** `keycloak/realm-export.json` (montado por Compose) y `scripts/keycloak/realm-export.json` (nunca usado) difieren en `directAccessGrantsEnabled` (`true` vs `false`). | Media | Eliminar la copia muerta o convertirla en enlace único |
| S-2 | No hay evidencia documentada del flujo de **refresh token** (la guía lo exige explícitamente). Además `revokeRefreshToken: false` — sin rotación. | Media | Test de API con `grant_type=refresh_token`; considerar activar la rotación |
| S-3 | Swagger UI y `/v3/api-docs` son públicos ("for grading convenience"). | Baja | Aceptable si se documenta como decisión consciente; cerrar en `prod` |
| S-4 | `JWT_SECRET` y `JWT_EXPIRATION_MS` en `.env.example` son residuos de un esquema anterior (hoy la firma la hace Keycloak). | Baja | Eliminar para evitar confusión y falsos positivos de secretos |
| S-5 | No hay OWASP Dependency Check ni Snyk. | **Alta** | Ver T-5 (requisito de la capa de Security Testing) |

---

### Seguridad obligatoria (checklist de la guía)

| Requisito | Estado | Evidencia |
|---|---|---|
| Keycloak + OAuth2 + JWT | Cumple | `SecurityConfig`, Authorization Code + PKCE |
| Roles | Cumple | 4 roles realm |
| Protección de endpoints | Cumple | 23/23 endpoints con `@PreAuthorize` + `anyRequest().authenticated()` |
| Refresh tokens | Cumple (reactivo) | Ver SEC-2 |
| Seguridad basada en scopes | Cumple | `hasAuthority('SCOPE_...')` en toda la capa web |
| Expiración de sesiones | Cumple | `accessTokenLifespan` 300s, `ssoSessionIdleTimeout` 1800s, `ssoSessionMaxLifespan` 36000s, `JwtTimestampValidator`, política `STATELESS` |

**SEC-1 · El CORS de staging apunta a un dominio inventado — el SPA quedaría bloqueado.**
`application-staging.yml` permite únicamente `https://staging.inventory.example.com`, un placeholder nunca actualizado. El workflow de staging, en cambio, sirve el frontend en `http://localhost:3000` y le inyecta `VITE_API_BASE_URL=http://localhost:8080`: peticiones cross-origin contra un backend que no reconoce ese origen, bloqueadas por CORS.

No se ha detectado porque **CORS lo aplica el navegador** y todas las pruebas de staging son `curl` y Maven, que lo ignoran. El único test capaz de verlo —Playwright— no se ejecuta en staging (T-2 / C-1).

`frontend/nginx.conf` sí proxea `/api/`, `/products` y `/categories` hacia el backend: con `VITE_API_BASE_URL` vacío el SPA sería same-origin y el problema no existiría. La configuración de staging fuerza al frontend a saltarse su propio proxy.
**Acción:** dejar `VITE_API_BASE_URL` vacío en staging (usar el proxy nginx) o declarar el origen real en `app.cors.allowed-origins`; y ejecutar E2E contra staging, que elimina toda esta clase de fallo.
*(Los tres bloques `location` distintos de `nginx.conf`, uno por convención de ruta, son consecuencia directa de A-1.)*

**SEC-2 · Refresh de token solo reactivo.**
`api.ts` renueva con `keycloak.updateToken(30)` dentro del interceptor de respuesta 401. Cumple el requisito, pero no hay `onTokenExpired` ni refresco proactivo: cada expiración (5 min) genera un 401 previo al reintento, y varias peticiones concurrentes que expiren a la vez disparan refrescos simultáneos sin cola ni mutex.
**Acción:** registrar `keycloak.onTokenExpired` para refrescar antes de vencer y serializar los reintentos.

## 3.bis Arquitectura técnica obligatoria — **COMPLETO**

| Requisito | Estado | Evidencia |
|---|---|---|
| Backend agnóstico | Cumple | Spring Boot 3 + Java 21 (recomendado por la guía) |
| Frontend agnóstico | Cumple | React 18 + TypeScript + Vite (recomendado) |
| Base de datos agnóstica | Cumple | PostgreSQL 16 (recomendado) |
| Contenedores | Cumple parcialmente | Docker + Docker Compose completos; **Podman sin ninguna mención** en el repositorio |
| Migraciones (Flyway o Liquibase) | Cumple con solidez | 7 migraciones Flyway versionadas, `validate-on-migrate: true`, `baseline-on-migrate: false`, `ddl-auto: validate` |

**Acción (barata):** verificar `podman-compose up` y dejar constancia del resultado en el README. La guía menciona Podman junto a Docker y Compose; documentar que se probó cuesta minutos y cierra el punto.

## 4. Full Stack Testing — 8 capas obligatorias

| # | Capa | Obligatorio | Estado | Evidencia |
|---|------|-------------|--------|-----------|
| 1 | Unit Testing | JUnit + Mockito | **Completo** | 27 clases de test; JaCoCo ~81% instrucciones / 71% ramas |
| 2 | Integration Testing | Testcontainers | **Parcial** | 3 ITs con Postgres real vía Testcontainers; **Keycloak nunca se prueba contra un servidor real** (TEST-1) |
| 3 | API / Contract Testing | Endpoints, contratos OpenAPI, status codes, payloads | **Parcial** | RestAssured en `pom.xml`; colección Postman de 20 requests con aserciones — pero **sin validación de contrato OpenAPI y sin ejecución en CI** |
| 4 | E2E Testing | Playwright/Cypress/Selenium | **Parcial** | 10 tests Playwright (auth, products, stock) — **no se ejecutan en ningún pipeline**; sin roles, sin responsive, sin snapshots |
| 5 | Security Testing | ZAP + Dependency Check/Snyk | **Parcial** | ZAP presente pero nominal (TEST-10); **sin validación de CORS** (TEST-11); **falta Dependency Check / Snyk** |
| 6 | Performance Testing | JMeter y/o k6 | **AUSENTE** | Ningún archivo en el repositorio |
| 7 | Data Testing | Migraciones, integridad, duplicados, constraints, seeds | **Parcial (~50%)** | Migraciones validadas implícitamente por los ITs; 1 test real de duplicados; **CHECK constraints y seeds sin cobertura** |
| 8 | Manual Exploratory Testing | Charters, bugs, escenarios | **AUSENTE** | Sin documentación |

### Detalle verificado de las capas 1–3

**Capa 1 · Unit Testing — CUMPLE.**
JUnit 5 + Mockito, 27 clases. Las tres áreas exigidas están cubiertas: servicios (`ProductServiceTest`, `ProductServiceImplExtendedTest`, `CategoryServiceTest`, `StockServiceTest`, `ReportServiceTest`+Extended, `StockAuditServiceTest`), validaciones (`ProductCreateRequestValidationTest`, `CategoryCreateRequestValidationTest`) y lógica de negocio (`computeNewStock`, stock insuficiente, mappers, specs). Cobertura real: **81% instrucciones / 71% ramas** (56 de 197 ramas sin cubrir).

**Capa 2 · Integration Testing — PARCIAL.**
Testcontainers correctamente aplicado en `ProductRepositoryIT`, `AuditIntegrationIT` y `StockServiceConcurrencyIT`, las tres con `PostgreSQLContainer("postgres:16-alpine")` real.

> **TEST-1 · Keycloak nunca se prueba contra un servidor real.** La guía exige "base de datos real, **Keycloak**, integraciones". Lo existente: `SecurityIntegrationTest` usa `@WebMvcTest` + `@MockBean JwtDecoder` + post-processor `jwt()` (token sintético), y `KeycloakJwtConverterTest` es un unit test con `Jwt` construido a mano. No hay ningún contenedor de Keycloak: nada valida firma, claim `iss` ni los scopes tal como Keycloak los emite realmente — justo la superficie donde G-6 levanta dudas.
> **Acción:** `dasniko/testcontainers-keycloak` importando `realm-export.json` + un IT que obtenga token real y ejecute endpoints protegidos por scope. Cierra la capa y verifica G-6 simultáneamente.

**Capa 3 · API / Contract Testing — la más débil de las tres.**

| Sub-requisito | Estado |
|---|---|
| Validación de endpoints | Cumple — 20 requests Postman con aserciones + smoke `curl` en ambos pipelines |
| Validación de status codes | Cumple — 200/201/400/401/403/404/409 |
| Validación de payloads | Parcial — solo propiedades sueltas (`to.have.property('id')`), sin validación de esquema |
| Validación de contratos OpenAPI | **AUSENTE** |

> **TEST-2 · RestAssured declarado y sin usar.** Está en `pom.xml` con cero ocurrencias en `src/test`. Es la única de las cinco herramientas sugeridas presente en el proyecto y su uso es nulo.
> **TEST-3 · Newman fuera de CI.** La colección Postman solo se ejecuta manualmente. Microcks, Schemathesis y Cucumber: ausentes.

### Defectos de configuración del arnés de pruebas

**TEST-4 · El quality gate de cobertura no se ejecuta nunca — y hoy fallaría.**
`jacoco:check` está vinculado a la fase `verify` con mínimo `BRANCH` de 0.80; la cobertura real de ramas es 71%. Pero CI ejecuta `./mvnw test` (nunca alcanza `verify`) y staging ejecuta `./mvnw failsafe:integration-test failsafe:verify`, que invoca *goals* de failsafe y no la fase `verify` del ciclo de vida. El gate está configurado, incumplido y silenciado. Al conectar SonarCloud (Q-1) aflora de inmediato.
**Acción:** ejecutar `./mvnw verify` en CI y tratar el fallo como bloqueante.

**TEST-5 · Los tests de integración no cuentan para la cobertura.**
El plugin failsafe declara `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` sin `@{argLine}`, por lo que el agente de JaCoCo no se inyecta (surefire sí lo hace). El 81% corresponde **solo a tests unitarios**: todo lo que ejercitan los ITs contra Postgres real se descarta.
**Acción:** `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` y reporte agregado unit + IT.

**TEST-6 · Exclusiones de JaCoCo apuntan a rutas inexistentes (explica parte del 71%).**
`jacoco:check` excluye `com/inventory/config/**` y `com/inventory/exception/**`; los paquetes reales son `com/inventory/common/config/**` y `com/inventory/common/exception/**`. Las exclusiones no filtran nada. `sonar.exclusions` sí acierta porque usa el glob `**/config/**`.
**Acción:** corregir las rutas — probablemente sube la cobertura de ramas sin escribir un solo test.

### Detalle verificado de las capas 4–6

**Capa 4 · E2E Testing — 3 de 6 sub-requisitos.**
Playwright cubre la herramienta obligatoria. 10 tests: auth (3), productos (4), stock (3).

| Sub-requisito | Estado |
|---|---|
| Flujos completos | Cumple — login → crear → editar → desactivar; movimiento de entrada; export CSV |
| Navegación | Cumple — routing SPA vía sidebar |
| Seguridad | Parcial — solo login fallido |
| Snapshots / Screenshots | **NO** (TEST-8) |
| Roles | **NO** (TEST-7) |
| Responsive | **NO** (TEST-9) |

> **TEST-7 · Los 10 tests se ejecutan como `inv_admin`.** Cero apariciones de `inv_clerk`, `inv_viewer` o `inv_auditor`. El modelo granular de permisos no tiene ninguna prueba extremo a extremo — y es justo donde se manifestarían G-4 (roles múltiples) y G-5 (default-allow).
> **TEST-8 · No hay snapshot testing.** Lo único presente es `screenshot: 'only-on-failure'` en la config, que es captura de diagnóstico ante fallo, no comparación visual. Cero `toHaveScreenshot()`.
> **TEST-9 · Un solo viewport.** `projects: [{ name: 'chromium', use: devices['Desktop Chrome'] }]`, sin `setViewportSize` ni proyectos móviles.

**Capa 5 · Security Testing — 4 de 6, con el escaneo ZAP casi vacío.**

| Sub-requisito | Estado |
|---|---|
| OWASP ZAP | Presente pero nominal (TEST-10) |
| Validación JWT | Cumple (con mocks — TEST-1) |
| Validación de permisos | Cumple — Postman 401/403, viewer bloqueado en staging y Jenkins |
| Validación de autenticación | Cumple — 401 sin token |
| Validación de CORS | **AUSENTE** (TEST-11) |
| Dependency Check / Snyk | **AUSENTE** (T-5) |

> **TEST-10 · El escaneo ZAP apenas toca la aplicación.** Se acumulan tres causas: `cmd_options: '-I'` hace que nunca falle el build; no existe `.zap/rules.tsv` (sin umbrales ni gestión de falsos positivos); y es un *baseline scan sin autenticación* contra una API donde todos los endpoints de negocio exigen JWT — en la práctica solo alcanza `/health`, `/swagger-ui` y `/v3/api-docs`. Cumple la casilla formal sin aportar señal.
> **Acción:** ZAP autenticado (context + token de Keycloak) o `zap-full-scan` con reglas y umbral bloqueante.
>
> **TEST-11 · CORS sin ninguna validación.** Ni en tests de backend, ni en Postman, ni en E2E. Es el hueco exacto por el que pasa SEC-1. Un test que verifique `Access-Control-Allow-Origin` por perfil lo habría detectado.

**Capa 6 · Performance Testing — AUSENTE (0 de 5).**
Cero referencias a k6, JMeter, Gatling, Locust o Artillery en todo el repositorio: sin archivos, sin scripts, sin pasos de pipeline. Ninguno de los cinco sub-requisitos (stress, load, concurrent users, tiempo de respuesta, throughput) tiene evidencia.
Lo único adyacente es `StockServiceConcurrencyIT`, que valida concurrencia con bloqueo pesimista a nivel de servicio: buena base conceptual —identifica la operación crítica bajo carga— pero es un test de integración y no cuenta como esta capa.
Junto con exploratory testing (capa 8), son las dos únicas capas en cero de las ocho.

### Detalle verificado de las capas 7–8

**Capa 7 · Data Testing — ~50%.**

> **Corrección respecto a la primera versión de este documento:** se afirmó que no había "tests dedicados de duplicados". Es **incorrecto**: `ProductRepositoryIT.save_duplicateSku_throwsConstraintViolation` valida el `UNIQUE` de SKU contra Postgres real esperando `DataIntegrityViolationException`.

| Sub-requisito | Estado |
|---|---|
| Migraciones | Cumple implícitamente — `application-test.yml` activa Flyway y los 3 ITs con Testcontainers ejecutan las 7 migraciones sobre base limpia en cada corrida; `validate-on-migrate: true` + `ddl-auto: validate` hacen fallar el arranque ante desajuste esquema/entidades. Falta un `flyway:validate` explícito en los pipelines |
| Datos duplicados | Parcial — cubierto para `products.sku`; sin equivalente para `categories.name` ni `app_users.keycloak_id` |
| Constraints | **Casi sin cobertura** (DATA-1) |
| Seeds | Bien construidos, sin verificar (DATA-2) |
| Integridad de datos | Esquema sólido (FKs `ON DELETE CASCADE`/`SET NULL`), sin tests directos |

> **DATA-1 · Los CHECK constraints no se prueban.** El esquema define `price >= 0`, `stock >= 0`, `quantity >= 0` y `type IN ('IN','OUT','ADJUSTMENT')`, pero el único constraint con test es el UNIQUE de SKU. La capa de servicio bloquea el stock negativo con `BusinessException` antes de llegar al motor, de modo que la defensa en profundidad existe pero nadie verifica que la base la respalde.
> **Acción:** tests de integración que inserten directamente valores inválidos y esperen violación de constraint.
>
> **DATA-2 · Los seeds no se verifican y los E2E dependen de ellos.** `V5__seed_data.sql` es idempotente (`ON CONFLICT DO NOTHING` en las tres tablas), pero ningún test afirma que cargue (5 categorías, 9 productos). Los E2E lo asumen: `selectOption({ index: 1 })` presupone categorías existentes y "lista de productos se carga" espera `count > 0`. Un seed fallido rompe los E2E con un error que no señala la causa.
>
> **DATA-3 · Esquema muerto de la era pre-Keycloak.** `V1` crea la tabla `users` **con columna `password`** y `V3` crea `app_users`, que es la realmente usada; coexisten las entidades `User` y `AppUser`, y `products.created_by` aún referencia `users(id)`. Una tabla de usuarios con hash de contraseña en desuso es deuda visible para Sonar y ruido en cualquier revisión de seguridad.
> **Acción:** migración que elimine la tabla y la entidad muertas, o justificación documentada si se conservan.

**Capa 8 · Manual Exploratory Testing — AUSENTE (0 de 3).**
Sin charters, sin escenarios explorados, sin bugs documentados; no existe `docs/testing/`.

Verificado también en el repositorio remoto: **13 issues, todas EPICs de fase** (Fase 0 → Fase 12), 7 cerradas y 6 abiertas. **Ningún issue de tipo bug.** El requisito de usar Issues se cumple, pero no hay ni un defecto registrado en todo el proyecto — lo que resta credibilidad a un sistema que declara ocho capas de testing.

Material de arranque ya verificado por este análisis, utilizable como primeros hallazgos: G-4 (roles múltiples pierden permisos), G-5 (fallback default-allow), SEC-1 (CORS de staging), E-1 (columnas nullable → `"null"` en el CSV), D-1 ("más vendidos" no mide ventas).

### Acciones detalladas

**T-1 · Contract testing OpenAPI (capa 3)**
- Añadir Schemathesis contra `http://localhost:8080/v3/api-docs` en el job de staging, o
- Validar respuestas contra el esquema OpenAPI con RestAssured (`matchesJsonSchemaInClasspath`).
- Ejecutar la colección Postman con Newman en CI: `newman run docs/postman/inventory-api.postman_collection.json -e <env>` y publicar el reporte como artefacto.
- Opcional (suma puntos): Cucumber para escenarios BDD sobre la API.

**T-2 · E2E en el pipeline (capa 4)**
- Añadir un step de Playwright en `.github/workflows/staging.yml` después de que el stack esté sano.
- Ampliar cobertura:
  - Tests por rol (`inv_admin`, `inv_clerk`, `inv_auditor`, `inv_viewer`) verificando que la UI oculta/bloquea acciones sin permiso.
  - Proyectos responsive en `playwright.config.ts` (mobile 375px, tablet 768px, desktop 1440px).
  - Snapshots visuales con `toHaveScreenshot()` — la guía los exige explícitamente.
- Publicar `playwright-report/` como artefacto de CI.
- Limpiar del repositorio los `test-results/*.zip` y `playwright-report/` versionados (ruido en el diff, ya hay `.gitignore` pero quedan archivos rastreados).

**T-3 · Performance testing (capa 6) — AUSENTE**
- Crear `perf/k6/`:
  - `load-test.js` — carga sostenida sobre `GET /products`.
  - `stress-test.js` — rampa hasta el punto de quiebre.
  - `spike-test.js` — usuarios concurrentes sobre `POST /api/stock/movements`.
- Umbrales explícitos: `http_req_duration p(95)<500ms`, `http_req_failed<1%`.
- Ejecutar contra staging desplegado y guardar el resumen JSON como evidencia.

**T-4 · Data testing (capa 7)**
- Tests de integración que verifiquen: unicidad de SKU, constraint de stock no negativo, FK producto↔categoría, ejecución limpia de las 7 migraciones sobre base vacía, e idempotencia de seeds.
- Añadir `flyway:validate` como step de pipeline.

**T-5 · Security testing (capa 5)**
- Añadir OWASP Dependency Check al `pom.xml` (`dependency-check-maven`) o `dependency-check-action` en GitHub Actions.
- Alternativa/complemento: `snyk/actions` para backend y frontend.
- Publicar el reporte HTML como artefacto.

**T-6 · Exploratory testing (capa 8) — AUSENTE**
- Crear `docs/testing/exploratory/`:
  - `charters.md` — misión, área, duración y tester por sesión.
  - `session-notes.md` — escenarios explorados y observaciones.
  - `bugs-encontrados.md` — hallazgos con severidad, pasos y evidencia.
- Vincular los bugs a Issues de GitHub para demostrar trazabilidad.

---

## 5. Entornos

### Development — **COMPLETO**
Compose completo, `application-dev.yml`, `.env.example`, CORS permisivo para localhost.

### Preview / Staging — **PARCIAL**

| Debe incluir | Estado |
|---|---|
| Base de datos | Cumple — Postgres 16 en el stack |
| Keycloak | Cumple — con espera activa a `/health/ready` |
| Docker Compose | Cumple — `docker compose up -d --build` |
| Variables de entorno reales | Cumple — `.env` generado desde GitHub Secrets |
| CI/CD | Cumple — `staging.yml` completo |
| Observabilidad | **Parcial** — Prometheus y Grafana arrancan por formar parte del compose, pero falta el stack exigido (Tempo, Loki, Alloy, Alertmanager), nada verifica que estén activos y no se captura evidencia |

**Pruebas contra el sistema desplegado — 2 de 4:**

| Tipo | Estado |
|---|---|
| API tests | **Cumple plenamente** — `curl` con JWT real obtenido de un Keycloak vivo, contra endpoints reales; incluye verificación de 401 y de que `inv_viewer` no puede crear productos |
| Security tests | Parcial — ZAP contra el sistema vivo, aunque nominal (TEST-10) |
| Integration tests | **NO — el paso es engañoso** (ENV-1) |
| E2E tests | **Ausente** (T-2 / C-1) |

**ENV-1 · El paso "Run IT tests against live DB" no toca la base desplegada.**
Invoca `StockServiceConcurrencyIT` pasándole `-Dspring.datasource.url=jdbc:postgresql://localhost:5432/inventory_db` y el secreto de contraseña real. Pero la clase declara `@Container PostgreSQLContainer` y un `@DynamicPropertySource` que registra `spring.datasource.url` desde ese contenedor. **`@DynamicPropertySource` tiene la precedencia más alta** en la resolución de propiedades de Spring, por encima de los `-D` de línea de comandos: el test levanta su propio Postgres efímero y jamás se conecta a la base de staging. El nombre del paso, su propósito declarado y el secreto que consume son falsos.
Al ser el único paso que aportaría "integration tests contra el sistema desplegado", ese sub-requisito queda sin cubrir mientras aparenta lo contrario.
**Acción:** escribir un IT que reciba la URL de la base por configuración externa (sin `@DynamicPropertySource`), o reemplazar el paso por pruebas de API que ya operan contra el sistema vivo.
~~**Verificar además:** el paso usa `./mvnw test` y surefire excluye `**/*IT.java`; con `-DfailIfNoTests=false`, si la exclusión prevalece el paso pasa en verde ejecutando **cero tests**.~~

**Comprobado y descartado (2026-07-23).** El run 29973867778 registra `Tests run: 2` para `StockServiceConcurrencyIT`: `-Dtest=` tiene prioridad sobre los `<excludes>` de surefire, así que el test sí se ejecutaba. La sospecha de "cero tests" era infundada.

Lo que sí confirma ese mismo run es la parte principal del hallazgo: `Creating container for image: postgres:16-alpine` seguido de `Container postgres:16-alpine started`. El test levantaba su Postgres efímero mientras consumía el secreto de la base de staging.

**Resuelto** por [`LiveDatabaseIT`](../backend/src/test/java/com/inventory/common/LiveDatabaseIT.java) y el perfil `live-db-it`.

**ENV-2 · La etapa "Integration Tests" de Jenkins tampoco es de integración.**
Ejecuta `-Dtest=SecurityIntegrationTest`, un `@WebMvcTest` con `@MockBean JwtDecoder`: slice test con mocks, sin base de datos ni contenedores. Jenkins nunca ejecuta los ITs reales de Testcontainers.
**Acción:** invocar `./mvnw verify` (failsafe) en esa etapa.

**ENV-3 · El staging es efímero, no persistente.**
Vive dentro del runner de GitHub Actions y se destruye con `docker compose down -v`. Es defendible —el sistema se despliega de verdad y se prueba por HTTP, que es lo que exige la guía— pero no existe un entorno preview accesible para el docente.
**Acción:** documentar la decisión y publicar evidencia como artefactos (logs de despliegue, capturas de Grafana con datos, reportes de pruebas).

### Production — **PARCIAL**
`production.yml` construye la imagen y crea el GitHub Release. No despliega ni verifica nada; no existe un entorno de producción real.
**Acción:** añadir un smoke test post-release, aunque sea contra un stack levantado con compose.

---

## 6. Observabilidad — BRECHA MÁS GRAVE (15%)

| Componente | Obligatorio | Estado |
|---|---|---|
| Metrics — Prometheus | Sí | **Completo** — cadena verificada de extremo a extremo; las 7 familias de métricas exigidas están expuestas |
| Traces — Tempo | Sí | **AUSENTE** |
| Logs — Loki | Sí | **AUSENTE** |
| Collector — Alloy | Sí | **AUSENTE** |
| Dashboards — Grafana | Sí | **Parcial** — 1 dashboard |
| Alerting — Alertmanager | Sí | **AUSENTE** |
| Instrumentación — OpenTelemetry | Sí | **AUSENTE** |

### Lo que sí funciona: la cadena de métricas

Conviene registrarlo porque es defendible en la presentación. El recorrido está completo y correcto:
- `micrometer-registry-prometheus` + Actuator exponen `/actuator/prometheus`
- `SecurityConfig` abre ese endpoint explícitamente (`PrometheusScrapeEndpoint` en `permitAll`), de modo que el scrape funciona sin token
- El target `inventory-backend:8080` resuelve por DNS de Docker
- Datasource de Grafana provisionado (`http://prometheus:9090`) y dashboard auto-cargado
- Histogramas y SLOs definidos a mano: `50ms, 100ms, 200ms, 500ms, 1s, 2s`

Las 7 familias de métricas exigidas (CPU, memoria, JVM, latencia, throughput, error rate, pool de BD) están disponibles y visualizadas. **Este sub-requisito cumple.**

### Detalle de las brechas

**OBS-1 · Configuración de tracing que no traza nada.**
Tres perfiles definen muestreo con valores graduados con criterio —base `1.0`, staging `0.5`, prod `0.1`— pero el `pom.xml` no contiene ninguna dependencia de tracing: ni `micrometer-tracing-bridge-otel`, ni exportador OTLP, ni Zipkin, ni Brave. Es configuración muerta en tres archivos: aparenta madurez de observabilidad sin producir una sola traza.

**OBS-2 · El dashboard de "Negocio" no mide negocio.**
`grep` de `MeterRegistry`, `Counter`, `Timer`, `@Timed` y `Gauge` en todo `src/main/java` devuelve **cero resultados**: no existe ninguna métrica de negocio personalizada. Los paneles de "Inventario — Métricas de Negocio" son tasas de peticiones HTTP por endpoint; "Movimientos de Stock (req/s por endpoint)" mide tráfico HTTP hacia esa ruta, no movimientos de inventario (un 403, un listado o un POST fallido cuentan igual). No se puede responder "cuántas salidas hubo hoy" ni "cuántos productos cruzaron el mínimo". Enlaza con E-3.

**OBS-3 · El dashboard de Seguridad es hoy inconstruible.**
Falta (1 de los 4 exigidos), pero la causa es más profunda: `prometheus.yml` solo define dos jobs, `prometheus` e `inventory-backend`. **Keycloak no se scrapea**, y no hay `node-exporter` ni `postgres-exporter`. Consecuencias: la alerta obligatoria de "fallos de autenticación" no tiene fuente de datos posible, y el dashboard de "Infraestructura" tampoco es construible de verdad — sin node-exporter no hay CPU ni memoria del host, solo de la JVM, que es la razón por la que el dashboard actual es JVM-céntrico.

**OBS-4 · Logs sin contexto: 1 de 6 campos.**
`logback-spring.xml` emite JSON en staging/prod (formato correcto), pero de los seis campos exigidos solo está `nivel`. Faltan `traceId`, `spanId`, `correlationId`, usuario y endpoint; no existe filtro que pueble el MDC (`grep MDC` en `src/main`: cero). Sin `traceId` no hay correlación posible entre logs y trazas, que es el propósito de tener Loki y Tempo juntos.

**OBS-5 · Cero alertas.**
Ninguna de las cinco obligatorias. No hay Alertmanager en el compose, ni archivo de reglas, ni alertas nativas de Grafana: el provisioning solo contiene `dashboards/` y `datasources/`. Las cuatro primeras son escribibles hoy con las métricas existentes; la quinta requiere resolver antes OBS-3.

**OBS-6 · Datasources incompletos.** Solo `prometheus.yml`; faltan `tempo.yml` y `loki.yml`.

### Plan de cierre (orden recomendado)

1. **Dependencias backend** (`pom.xml`):
   ```xml
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-tracing-bridge-otel</artifactId>
   </dependency>
   <dependency>
     <groupId>io.opentelemetry</groupId>
     <artifactId>opentelemetry-exporter-otlp</artifactId>
   </dependency>
   ```
   Configurar `management.otlp.tracing.endpoint: http://alloy:4318/v1/traces`.

2. **Logs correlacionados**: añadir `logstash-logback-encoder` y patrón con `%mdc`, más un filtro que inyecte `correlationId`, usuario (`sub` del JWT) y endpoint en el MDC por request.

3. **Servicios en compose**: `tempo`, `loki`, `alloy`, `alertmanager` con sus archivos de configuración en `observability/`.

4. **Datasources Grafana**: `tempo.yml` y `loki.yml`, con correlación traces↔logs habilitada.

5. **Dashboards**: separar el actual en Infraestructura y Aplicación; mantener Negocio; crear **Seguridad** (401/403 por endpoint, fallos de login en Keycloak, tokens rechazados, accesos denegados por scope).

6. **Reglas de alerta**: `observability/prometheus/rules/alerts.yml` con las 5 alertas obligatorias + routing en Alertmanager.

7. **Levantar la observabilidad completa en staging** — la guía exige que el entorno preview incluya observabilidad.

---

## 7. Calidad de código

| Aspecto | Estado |
|---|---|
| Plugin Sonar en `pom.xml` | Configurado (`sonar-maven-plugin` 4.0.0.4121, `sonar.host.url: http://localhost:9000` — SonarQube local, no SonarCloud) |
| Ejecución de Sonar | **NUNCA** — sin `sonar-project.properties`, sin `SONAR_TOKEN` en `GITHUB_SECRETS.md`, sin job en ningún workflow |
| JaCoCo | Configurado, umbral 80%; actual ~81% instrucciones / **71% ramas**; gate nunca invocado (TEST-4) |
| Spotless | Configurado en fase `validate` y **desactivado con `-Dspotless.check.skip=true` en todos los pipelines sin excepción** |
| Calidad del frontend | **Sin cobertura de CI alguna** (Q-3) |

**Q-1 · Sonar nunca se ha ejecutado.** Las cinco métricas exigidas —Coverage, Bugs, Vulnerabilities, Code smells, Duplicación— no tienen ninguna evidencia. Es un requisito obligatorio que representa el 10% de la nota.
**Acción:** job de SonarCloud en GitHub Actions con `SONAR_TOKEN` y `sonar.projectKey`; badge en el README.

**Q-2 · Spotless configurado y evadido sistemáticamente.** El flag `-Dspotless.check.skip=true` aparece en ci.yml (ambos jobs), production.yml, staging.yml y las cuatro etapas del Jenkinsfile. No es ausencia de control de formato: es un control puesto y desactivado en todas partes.

**Q-3 · El frontend no tiene ninguna cobertura de CI.** Existen 3 archivos de test Vitest, configuración ESLint y los scripts `test`, `test:coverage` y `lint`. Ningún workflow los ejecuta; la única aparición de "frontend" en el CI es el `client_id` de Keycloak. Los fallos de compilación afloran al construir la imagen, pero tests y lint nunca corren automáticamente.

**Q-4 · Cobertura sin publicar.** El badge del README sigue en `placeholder` y no se publica ningún reporte. Junto con TEST-4 y TEST-5, la cobertura no está ni medida correctamente ni gobernada.

**Q-5 · Duplicación real ya identificada**, que es justo lo que Sonar debería vigilar: G-3 (lógica de permisos duplicada Java/TypeScript), G-7 (`realm-export.json` duplicado y divergente), DATA-3 (`User` y `AppUser` coexistiendo).

---

## 8. CI/CD

### Pipeline obligatorio vs. implementado

### Estado real de ejecución (verificado contra el historial de GitHub Actions)

| Workflow | Ejecuciones | Última | Situación |
|---|---|---|---|
| CI — Unit Tests | 34 | 2026-06-15 | Activo |
| Staging — Deploy & Test | 21 | **2026-06-05** | **Dormido** (CI-1) |
| Promote to Staging | 7 | 2026-06-12 | Residual |
| Production — Release | **0** | — | **Nunca ejecutado** (CI-2) |

**CI-1 · El pipeline de staging lleva parado desde el 2026-06-05.**
Se ejecutó 21 veces, siempre por push a `develop`. La estrategia de ramas migró a `feature/* → main` (las 10 PRs recientes apuntan todas a `main`), así que nadie empuja ya a `develop` y `staging.yml` quedó inactivo.
**Todo lo mergeado después del 5 de junio nunca pasó por despliegue, API tests contra sistema vivo ni ZAP:** el SPA React completo (#25), los tests E2E (#27), los ITs con Testcontainers (#26), el pipeline de Jenkins (#24), el dashboard de Grafana (#23) y las aserciones Postman (#28). Es decir, casi todo lo que sustenta las áreas de Testing y Observabilidad.
Explica además por qué SEC-1 sigue sin detectarse: el pipeline que lo expondría no corre desde antes de que existiera el frontend.
**Acción:** añadir `main` a los triggers de `staging.yml` (o restablecer el flujo por `develop`) y volver a ejecutarlo antes de la entrega.

**CI-2 · `production.yml` nunca se ha ejecutado.** Cero runs; dispara con tags `v*.*.*` y no existe ninguno. La etapa de release es código sin probar.

**CI-3 · Branch protection existe, pero no exige CI en verde.**
`main` **sí** está protegida: 1 aprobación requerida, `dismiss_stale_reviews`, sin force push ni deletion. Pero `required_status_checks` responde *404 – not enabled*: se puede mergear con el CI en rojo. Además `enforce_admins: false` permite al propietario saltarse la regla.
**Acción:** exigir el check de CI como status check obligatorio.

**CI-4 · Topología de ramas contradictoria.**
Coexisten `develop`, `develop-rebuilt`, `staging`, `backup-develop`, `backup-develop-rebuilt` y 11 ramas `feature/*` sin borrar. Dos flujos en paralelo: `staging.yml` escucha `develop` mientras `promote-to-staging.yml` escucha `develop-rebuilt` y mergea a la rama `staging`. El flujo real ignora ambos.

### Pipeline obligatorio — 10 etapas

| Etapa | GitHub Actions | Jenkins |
|---|---|---|
| Checkout | Sí | Sí |
| Build | Sí | Sí |
| Unit tests | Sí | Sí |
| Integration tests | Sí (failsafe real) | **No** — slice test con mocks (ENV-2) |
| API tests | Sí, pero en pipeline dormido (CI-1) | Sí |
| E2E tests | **No** | **No** |
| Security scan | ZAP sí / Dependency Check **no** | **Ninguno** |
| Quality gates | **No** | **No** (solo un mensaje `unstable` que nada dispara) |
| Docker build | Sí | Sí |
| Deployment | Staging dormido / producción nunca ejecutada | Sí (stack local) |

Aproximadamente **6,5 de 10** en GitHub Actions y **6 de 10** en Jenkins, contando etapas existentes — no las que hacen lo que su nombre declara.

**Acciones:**
- **C-1:** añadir job de E2E Playwright en `staging.yml` (cierra también T-2).
- **C-2:** añadir Dependency Check/Snyk y Sonar Quality Gate como etapas bloqueantes.
- **C-3:** añadir k6 al pipeline de staging (cierra T-3).
- **C-4:** añadir etapas equivalentes en `Jenkinsfile` — la guía exige "pipeline visual completo"; hoy Jenkins tiene menos etapas que GitHub Actions.
- **C-5:** capturar screenshot del pipeline Jenkins en verde como evidencia de entrega.

---

## 9. Documentación — BRECHA GRAVE (10%)

| Entregable exigido | Estado |
|---|---|
| Documento de requisitos funcionales y no funcionales | **AUSENTE** |
| Documentación técnica: diagramas de arquitectura | **AUSENTE** |
| Documentación técnica: guía de instalación | Parcial (README "Inicio Rápido") |
| Documentación técnica: manual de mantenimiento | **AUSENTE** |
| Guía de pruebas: casos, resultados, defectos | **AUSENTE** |

Contenido actual de `docs/`: un ADR, `GITHUB_SECRETS.md`, la colección Postman, un `.docx` de plan y `docs/api/` vacío (solo `.gitkeep`).

**DOC-1 · El `.docx` es un plan de trabajo, no un documento de requisitos.**
`docs/Plan_Proyecto_Final_QAS.docx` contiene ~21.000 caracteres de *"Plan de Trabajo por Fases y Tareas"*: Fase 0 → Fase 12, distribución entre los 2 integrantes y ruta crítica, en correspondencia exacta con las 13 EPICs del repositorio. Es buena evidencia de planificación, pero no satisface el entregable (a), que exige requisitos funcionales y no funcionales.

**DOC-2 · `docs/api/` está vacío teniendo el generador ya escrito.**
El `pom.xml` define el perfil `generate-docs`, que arranca la aplicación, consulta `/v3/api-docs.yaml` y escribe `docs/api/openapi.yaml`. Nunca se ha ejecutado y el directorio solo contiene `.gitkeep`. Es un comando de ejecutar y commitear.

**Estructura propuesta:**

```
docs/
├── requisitos/
│   └── requisitos-funcionales-y-no-funcionales.md
├── arquitectura/
│   ├── arquitectura-general.md          # diagrama de componentes
│   ├── diagrama-despliegue.md           # topología Docker
│   ├── modelo-de-datos.md               # ERD
│   └── flujo-autenticacion.md           # secuencia OAuth2/JWT
├── operacion/
│   ├── guia-instalacion.md
│   └── manual-mantenimiento.md          # backups, migraciones, rotación de secretos, runbooks de alertas
├── testing/
│   ├── guia-de-pruebas.md               # estrategia + casos + resultados
│   ├── exploratory/                     # charters, notas, bugs
│   └── reportes/                        # JaCoCo, ZAP, k6, Playwright, Newman
└── decisions/
```

Los diagramas pueden hacerse en Mermaid dentro del Markdown — se renderizan en GitHub y evitan dependencias externas.

**Correcciones del README:**
- **D-1:** la tabla de stack anuncia Tempo, Loki, Alloy, Alertmanager, OpenTelemetry y k6 — **ninguno existe hoy**. O se implementan (recomendado) o se corrige el README. Un evaluador que verifique esto lo leerá como sobredeclaración.
- **D-2:** el badge de coverage dice `placeholder`.
- **D-3:** el ejemplo de `@PreAuthorize` en el README usa `hasRole(...)` y `SCOPE_email`, no el modelo de permisos real del sistema.
- **D-4:** `package.json` raíz tiene el bloque de badges pegado dentro del campo `description`.

---

## 10. Buenas prácticas y repositorio

| Práctica | Estado |
|---|---|
| Conventional Commits | **Cumple** — 20 de los últimos 25 commits conformes; los 5 restantes son merge commits autogenerados por GitHub (excepción estándar). `commitlint` + hook `commit-msg` activos. `.husky/pre-commit` está vacío (1 byte) |
| Pull Requests | **Cumple** — 29 PRs |
| Code Reviews | **Casi ausente** (BP-1) |
| Branch protection | Parcial — existe pero sin status checks obligatorios (CI-3) |
| Secrets management | **Cumple** — `.env` no rastreado, en `.gitignore` y **ausente de todo el historial** (`git log --all -- .env` vacío); GitHub Secrets y credenciales Jenkins correctamente usados |
| Variables de entorno | Cumple |
| Sin credenciales hardcodeadas | **1 violación** (BP-2) |
| Repositorio público + README profesional | Cumple, con las correcciones D-1…D-4 pendientes |
| Issues | Cumple — 13 issues, aunque todas EPICs y ningún bug (capa 8) |
| Participación equitativa (2 integrantes) | **Riesgo** — las 10 PRs recientes están firmadas por `Gameoversv`; criterio de evaluación explícito |

**BP-1 · Los code reviews no se están realizando.**
De las 10 PRs más recientes: seis (#20, #23, #24, #25, #26, #27) se mergearon con **0 reviews**; solo #21 y #22 tienen aprobación real; #28 se mergeó con una review sin aprobar; #29 sigue abierta sin revisar.
Enlaza con CI-3: la protección de `main` exige 1 aprobación, pero `enforce_admins: false` permite al propietario saltársela, y así ocurrió en 6 de 10 casos. Es un criterio explícito de la guía y trivial de verificar por el docente.

**BP-2 · Un secreto hardcodeado.**
`keycloak/realm-export.json:77` contiene `"secret": "inv-backend-secret-changeme-dev"`. Es un placeholder de desarrollo, pero reside en un repositorio público y corresponde literalmente al ítem "No hardcoded credentials" del checklist obligatorio. Debe tomarse de `${KC_BACKEND_CLIENT_SECRET}`, variable ya presente en `.env.example` y en el compose. Las contraseñas de test de los E2E (`Admin123`) son menores, pero conviene parametrizarlas.

**BP-3 · El `CONTRIBUTING.md` contradice la práctica real.**
Documenta "crea una rama desde `develop`" y "abre un PR hacia `develop`", mientras las 10 PRs recientes van `feature/* → main`. Es la misma incoherencia de CI-4 y la causa directa de que el pipeline de staging quedara dormido (CI-1).

---

## 11. Plan de trabajo priorizado

### Bloque 1 — Máximo retorno (Observabilidad, 15%)
1. Dependencias OpenTelemetry + exporter OTLP en el backend.
2. Alloy, Tempo, Loki, Alertmanager en `docker-compose.yml` con sus configs.
3. Logs con `traceId`, `spanId`, `correlationId`, usuario y endpoint (filtro MDC).
4. Datasources de Tempo y Loki en Grafana.
5. Cuatro dashboards separados, incluyendo Seguridad.
6. Cinco reglas de alerta + routing en Alertmanager.

### Bloque 2 — Testing (20%)
7. Suite k6 (load, stress, spike) con umbrales.
8. E2E Playwright en el pipeline: roles + responsive + snapshots.
9. Newman + validación de contrato OpenAPI en CI.
10. Dependency Check / Snyk.
11. Tests de datos (constraints, duplicados, migraciones, seeds).
12. Evidencia de exploratory testing.

### Bloque 3 — Documentación (10%)
13. Documento de requisitos.
14. Documentación técnica con diagramas.
15. Guía de pruebas con casos, resultados y defectos.
16. Manual de mantenimiento.
17. Corregir el README (stack real, badges, ejemplos de permisos).

### Bloque 4 — Calidad y CI/CD (25%)
18. SonarCloud en CI + badge.
19. Quality gates bloqueantes.
20. Cobertura de ramas ≥80%.
21. Dejar de saltar Spotless.
22. Igualar las etapas del `Jenkinsfile` a las de GitHub Actions.
23. Verificación post-deploy en producción.

### Bloque 5 — Cierre
24. Verificar branch protection y participación de ambos integrantes.
25. Recopilar evidencias QA en `docs/testing/reportes/`.
26. Preparar la presentación final funcional.

---

## 12. Checklist de entregables

| Entregable | Estado |
|---|---|
| Código fuente completo | Listo |
| Docker Compose funcional | Listo (falta stack de observabilidad) |
| Jenkins pipeline | Listo (faltan etapas) |
| GitHub Actions pipeline | Listo (faltan etapas) |
| Dashboards Grafana | Parcial — 1 de 4 |
| Reportes de pruebas | Parcial — faltan k6, Newman, Dependency Check, Playwright |
| Evidencias QA | **Ausente** — falta exploratory testing |
| Documentación completa | **Ausente** |
| Presentación final funcional | Pendiente |
