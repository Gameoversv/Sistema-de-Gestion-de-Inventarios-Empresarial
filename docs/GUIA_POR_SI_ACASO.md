**URLs de la demo:**

| Servicio | URL | Credencial |
|---|---|---|
| Frontend | http://localhost:3000 | `inv_admin` / `Admin123` |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Keycloak | http://localhost:8180 | `admin` / `admin123` |
| Grafana | http://localhost:3001 | `admin` / `changeme_grafana_password` |
| Prometheus | http://localhost:9090 | — |
| Alertmanager | http://localhost:9093 | — |

**Token para las pruebas con `curl`** (se reutiliza en todo el documento):

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/inventory/protocol/openid-connect/token \
  -d "grant_type=password" -d "client_id=inventory-frontend" \
  -d "username=inv_admin" -d "password=$KC_USER_ADMIN_PASSWORD" \
  --data-urlencode "scope=openid profile email product:view product:manage stock:view stock:manage report:view audit:view user:manage" \
  | jq -r '.access_token')
```

## 1. Alcance Funcional

### 1.1 Gestión de Productos

#### a. Agregar Producto

`POST /products` recibe los ocho campos que se piden —nombre, SKU, descripción, categoría, precio, cantidad inicial, stock mínimo y estado activo/inactivo—. Se implemento bean validation en el DTO para cumplir con lo siguiente:SKU obligatorio y único, precio ≥ 0, límites de longitud y el rechazo por duplicado.

- Controlador: [`ProductController.java:84`](../backend/src/main/java/com/inventory/product/web/ProductController.java)
- DTO y validación: [`ProductCreateRequest.java`](../backend/src/main/java/com/inventory/product/dto/ProductCreateRequest.java)
- Entidad: [`Product.java`](../backend/src/main/java/com/inventory/product/domain/Product.java)
- UI: [`ProductFormModal.tsx`](../frontend/src/pages/products/ProductFormModal.tsx)

#### Prueba

En la UI: http://localhost:3000/products → botón **Nuevo producto**. 

Repetir lo mismo para enseñar el rechazo por SKU duplicado (409).

#### b. Editar Producto

`PUT /products/{id}`, protegido por `product:manage`. Cada edición genera una revisión de Envers.

 - Controlador: [`ProductController.java:126`](../backend/src/main/java/com/inventory/product/web/ProductController.java) 
 - UI: [`ProductFormModal.tsx`](../frontend/src/pages/products/ProductFormModal.tsx) (mismo modal, modo edición).

#### Prueba

En la UI: http://localhost:3000/products → botón **Actualizar producto (El Lapiz)**

#### c. Eliminar Producto

 **Soft delete**, no borrado físico: el producto se marca inactivo y desaparece de los listados, pero sus movimientos de stock y su histórico de auditoría siguen siendo consultables. Borrarlo de verdad rompería la integridad del historial. 

  - Controlador: [`ProductController.java:172`](../backend/src/main/java/com/inventory/product/web/ProductController.java) 
  - Documentacion: [`ADR-003-soft-delete-de-productos.md`](decisions/ADR-003-soft-delete-de-productos.md)  
  - UI: [`DeleteConfirmModal.tsx`](../frontend/src/pages/products/DeleteConfirmModal.tsx).

#### Prueba

En la UI: http://localhost:3000/products → botón **borrar producto (El trashcan)**


#### d. Visualizar Productos (paginación, búsqueda, filtros, ordenamiento)

 Las cuatro opciones existen en la misma consulta: `Specification` de JPA para búsqueda y filtro por categoría, `Pageable` para paginación y ordenamiento por cualquier columna. El frontend expone las cuatro en la tabla.

 - Controlador: [`ProductController.java:51`](../backend/src/main/java/com/inventory/product/web/ProductController.java)  
 - Specification: [`product/repository/ProductSpecification.java`](../backend/src/main/java/com/inventory/product/repository/ProductSpecification.java)
 - UI: [`ProductsPage.tsx`](../frontend/src/pages/products/ProductsPage.tsx) (estado `search`, `page`, `sortBy`, `sortDir`).

#### Prueba

En la UI: http://localhost:3000/products → escribir en el buscador, cambiar de categoría, hacer clic en las cabeceras `SKU`/`Nombre`/`Precio` para ordenar y paginar abajo. Por API:

### 1.2 Control de Stock

#### a. Actualizar Stock (entrada y salida)

 Un único endpoint `POST /api/stock/movements` con `type: IN | OUT`, que ajusta la cantidad y deja el rastro en la misma transacción. La concurrencia está contemplada: dos movimientos simultáneos sobre el mismo producto no dejan el stock inconsistente, y hay un test de integración que lo prueba con base real.

 - Controlador: [`StockController.java:58`](../backend/src/main/java/com/inventory/stock/web/StockController.java) 
 - Implementacion: [`StockServiceImpl.java`](../backend/src/main/java/com/inventory/stock/service/StockServiceImpl.java) 
 - IT Test: [`StockServiceConcurrencyIT.java`](../backend/src/test/java/com/inventory/stock/service/StockServiceConcurrencyIT.java)
 - UI: [`StockPage.tsx`](../frontend/src/pages/stock/StockPage.tsx).

#### Prueba

En la UI: http://localhost:3000/stock → Registrar movimiento → Seleccionar producto → Seleccionar tipo de movimiento → Introduccir cantidad → Introducir motivo → hacer click en boton `Registrar movimiento`

#### b. Alertas por stock mínimo

 Tres niveles, no uno: el endpoint `GET /api/stock/alerts` para la API, la tarjeta de la UI, y una **métrica de negocio** que alimenta una alerta de Prometheus (`ProductosBajoMinimo`). Es decir, cruzar el mínimo no solo se ve en pantalla: dispara telemetría.

 - Controlador: [`StockController.java:146`](../backend/src/main/java/com/inventory/stock/web/StockController.java) 
 - Metrica: [`StockMetrics.java`](../backend/src/main/java/com/inventory/stock/metrics/StockMetrics.java) 
 - Regla: [`alerts.yml:104`](../observability/prometheus/rules/alerts.yml).

#### Prueba

En la UI: http://localhost:3000/ → Card de Stock minimo

En Grafana : http://localhost:3001 → dashboard**3 · Negocio** → panel *Productos bajo mínimo* / *Cruces de mínimo por SKU (top 10)*


#### c. Historial de Movimientos

Cada movimiento persiste los siguientes campos: fecha, usuario (extraído del JWT), tipo, **cantidad anterior**, cantidad nueva y observaciones. Las dos cantidades son snapshots explícitos añadidos en la migración `V7`, no un cálculo posterior: recalcularlos daría números falsos si un movimiento se corrige.

- Entidad: [`stock/domain/StockMovement.java`](../backend/src/main/java/com/inventory/stock/domain/StockMovement.java)
- Migración: [`V7__add_stock_movement_snapshots.sql`](../backend/src/main/resources/db/migration/V7__add_stock_movement_snapshots.sql)
- Consulta: [`StockController.java:105`](../backend/src/main/java/com/inventory/stock/web/StockController.java).

#### Prueba

```bash
curl -s "http://localhost:8080/api/stock/movements?page=0&size=5" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '.content[] | {fecha:.createdAt, usuario:.createdBy, tipo:.type,
                      antes:.previousQuantity, despues:.newQuantity, obs:.reason}'
```

Los seis campos exigidos salen en una sola respuesta.

#### d. Auditoría (Hibernate Envers)

 **Hibernate Envers**, sobre cuatro entidades (`Product`, `Category`, `StockMovement`, `AppUser`). Las tablas `*_aud` y la `revinfo` se crean por migración Flyway explícita, no dejando que Hibernate las genere: así el esquema de auditoría está versionado igual que el resto. Encima se expone una API de consulta protegida por `audit:view`.

 `@Audited` en las cuatro entidades 
 - Entidad de revisión: [`RevisionInfo.java`](../backend/src/main/java/com/inventory/audit/domain/RevisionInfo.java) 
 - Migración: [`V4__envers_audit_tables.sql`](../backend/src/main/resources/db/migration/V4__envers_audit_tables.sql) 
 - API: [`AuditController.java`](../backend/src/main/java/com/inventory/audit/web/AuditController.java) 
 - UI: [`AuditPage.tsx`](../frontend/src/pages/audit/AuditPage.tsx) 
 - TestIT: [`AuditIntegrationIT.java`](../backend/src/test/java/com/inventory/audit/AuditIntegrationIT.java)
 - ControllerTest: [`AuditControllerTest.java`](../backend/src/test/java/com/inventory/audit/web/AuditControllerTest.java)
 - StockAuditTest: [`StockAuditServiceTest.java`](../backend/src/test/java/com/inventory/audit/service/StockAuditServiceTest.java)
 - UnifiedAuditTest: [`UnifiedAuditServiceTest.java`](../backend/src/test/java/com/inventory/audit/service/UnifiedAuditServiceTest.java)

#### Prueba

Después de haber editado y borrado el producto → En la UI: http://localhost:3000/audit (solo visible para `inv_admin` e `inv_auditor`)

Se ven las revisiones ADD(agregado) / MOD(modificado) / DEL(borrado) con su usuario y timestamp. Para enseñar que Envers escribe de verdad en la base:


### 1.3 API Empresarial

#### a. API REST documentada con OpenAPI y Swagger UI

 springdoc genera el contrato desde el código y sirve Swagger UI. El contrato **además se versiona** en `docs/api/openapi.yaml`, y eso no es cosmético: es el fichero contra el que corre el contract testing  y con el que se siembra el escaneo ZAP mas adelante.

- OpenApiConfig: [`common/config/OpenApiConfig.java`](../backend/src/main/java/com/inventory/common/config/OpenApiConfig.java)
- Contrato versionado: [`docs/api/openapi.yaml`](api/openapi.yaml)  
- Perfil de regeneración `generate-docs` en [`backend/pom.xml`](../backend/pom.xml).

#### Prueba

Abrir http://localhost:8080/swagger-ui.html y ejecutar un endpoint desde ahí con el botón *Authorize*.

#### b. CRUD, inventario, movimientos y reportes

Los cuatro bloques existen como controladores separados, cada uno con su scope.

| Bloque | Rutas | Scope |
|---|---|---|
| CRUD productos / categorías | `/products`, `/categories` | `product:view` · `product:manage` |
| Consulta de inventario | `/api/stock/movements`, `/api/stock/alerts` | `stock:view` |
| Movimientos de stock | `POST /api/stock/movements` | `stock:manage` |
| Reportes | `/api/reports/{stock-summary,low-stock,critical-stock,top-products,dashboard-metrics,recent-movements}` | `report:view` |

 - ProductController: [`ProductController.java:84`](../backend/src/main/java/com/inventory/product/web/ProductController.java)
 - CategoryController: [`CategoryController.java:84`](../backend/src/main/java/com/inventory/product/web/CategoryController.java)
 - StockController: [`StockController.java:84`](../backend/src/main/java/com/inventory/stock/web/StockController.java)
 - ReportController: [`ReportController.java:84`](../backend/src/main/java/com/inventory/report/web/ReportController.java)

### 1.4 Interfaz de Usuario Amigable

#### a, b, c. Dashboard, usabilidad y los cinco bloques exigidos

SPA en React 19 + TypeScript, con navegación por sidebar y todo elemento protegido detrás de `PermissionGuard`: la UI no enseña acciones que el token no permite. El dashboard cubre los cinco bloques siguientes:

| Exigido | Cómo aparece |
|---|---|
| Productos críticos | Lista completa  |
| Productos más vendidos | Gráfico *Top 8*, desde `/api/reports/top-products` |
| Historial reciente | Panel *Movimientos recientes* |
| Métricas del sistema | Tarjetas: productos activos, valor de inventario |
| Indicadores operacionales | Tarjetas: bajo mínimo, stock crítico |

- UI: [`DashboardPage.tsx`](../frontend/src/pages/DashboardPage.tsx) 
- Guard: [`PermissionGuard.tsx`](../frontend/src/components/auth/PermissionGuard.tsx) 
- Layout: [`layout.tsx`](../frontend/src/components/layout/Layout.tsx)
- Header: [`Header.tsx`](../frontend/src/components/layout/Header.tsx)
- Sidebar: [`Sidebar.tsx`](../frontend/src/components/layout/Sidebar.tsx)
- Test de accesibilidad [`a11y.spec.ts`](../e2e/tests/a11y.spec.ts).

#### Prueba

Entrar a http://localhost:3000 como `inv_admin` `Admin123`  y recorrer el dashboard señalando los cinco bloques. Después, cerrar sesión y entrar como `inv_viewer` `Viewer123`: los botones de alta/edición desaparecen.

## 2. Roles y Niveles de Acceso (Modelo Granular Obligatorio)

Los siete permisos de la matriz del enunciado existen como **OAuth2 scopes en Keycloak**, no como roles. Los controladores autorizan con `@PreAuthorize("hasAuthority('SCOPE_product:manage')")` — **por scope, nunca por nombre de rol**

La matriz completa, tal como la pide el PDF:

| Módulo | Permiso | Descripción | Dónde se exige |
|---|---|---|---|
| Productos | `product:view` | Ver productos | [`ProductController.java:51,67`](../backend/src/main/java/com/inventory/product/web/ProductController.java) |
| Productos | `product:manage` | Crear, editar, eliminar | [`ProductController:84,126,149,172`](../backend/src/main/java/com/inventory/product/web/ProductController.java) |
| Stock | `stock:view` | Ver existencia e historial | [`StockController:105,122,146`](../backend/src/main/java/com/inventory/stock/web/StockController.java) |
| Stock | `stock:manage` | Entradas, salidas, ajustes | [`StockController:58`](../backend/src/main/java/com/inventory/stock/web/StockController.java) |
| Reportes | `report:view` | Reportes y dashboard | [`ReportController:50…169`](../backend/src/main/java/com/inventory/report/web/ReportController.java) |
| Seguridad | `user:manage` | Gestionar usuarios y roles | [`UserController:41`](../backend/src/main/java/com/inventory/user/web/UserController.java) |
| Auditoría | `audit:view` | Consultar auditoría | [`AuditController:51,74,93`](../backend/src/main/java/com/inventory/audit/web/AuditController.java)  |

Los roles se construyen **como combinaciones** de esos permisos:

| Rol de realm | Usuario demo | Scopes concedidos |
|---|---|---|
| `inventory-admin` | `inv_admin` | los siete |
| `warehouse-clerk` | `inv_clerk` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
| `auditor` | `inv_auditor` | `product:view`, `stock:view`, `report:view`, `audit:view` |
| `viewer` | `inv_viewer` | `product:view`, `stock:view`, `report:view` |


- Realm: [`keycloak/realm-export.json`](../keycloak/realm-export.json) 
- Asignación de scopes por rol: `assign_scope_roles` de [`scripts/keycloak/init-users.sh`](../scripts/keycloak/init-users.sh)  
- Conversión del token en el bean: `keycloakJwtConverter()` de [`common/config/SecurityConfig.java:133`](../backend/src/main/java/com/inventory/common/config/SecurityConfig.java)  
- Documentacion: [`ADR-004`](decisions/ADR-004-keycloak-autoridad-de-scopes.md) 
- Deuda de la primera presentacion "el profe dijo que era un disparate lo que tenia": [`G-4-G-5-scopes-por-rol.md`](testing/reportes/G-4-G-5-scopes-por-rol.md).

### Prueba

En Keycloak, visualmente:** http://localhost:8180 → realm `inventory` → *Client scopes* (los siete) → *Realm roles* → un rol → pestaña *Scope*.

## 3. Seguridad Obligatoria

Keycloak 24 + OAuth2 + JWT como resource server de Spring Security. Los cinco puntos:

| Exigido | Cómo |
|---|---|
| Roles | Cuatro roles de realm, mapeados a `ROLE_*` |
| Protección de endpoints | `@PreAuthorize` por scope en cada operación crítica; todo lo demás denegado por defecto |
| Refresh tokens | `grant_type=refresh_token` verificado en CI contra un Keycloak vivo |
| Seguridad basada en scopes | El `JwtAuthenticationConverter` deriva autoridades `SCOPE_*` del claim `scope` |
| Expiración de sesiones | Ciclo de vida del token verificado: emisión, refresco, expiración y rechazo del caducado |

Los dos rechazos se distinguen deliberadamente: **401** = no hay identidad; **403** = hay identidad pero faltan permisos. El dashboard de Seguridad de Grafana los separa por ese motivo.

- Configuracion: [`SecurityConfig.java`](../backend/src/main/java/com/inventory/common/config/SecurityConfig.java)
- CORS por perfil: [`application-{dev,staging,prod,demo}.yml`](../backend/src/main/resources/application-prod.yml) bajo `app.cors.allowed-origins` 
- ITtests: [`SecurityIntegrationTest`](../backend/src/test/java/com/inventory/common/config/SecurityIntegrationTest.java)
- ConverterTest: [`KeycloakJwtConverterTest`](../backend/src/test/java/com/inventory/common/config/KeycloakJwtConverterTest.java)
- CorsTest: [`CorsHttpTest`](../backend/src/test/java/com/inventory/common/config/CorsHttpTest.java)
- ProfileTest: [`CorsProfilesTest`](../backend/src/test/java/com/inventory/common/config/CorsProfilesTest.java)
- TestKeycloak: [`KeycloakAuthIT`](../backend/src/test/java/com/inventory/security/KeycloakAuthIT.java)
- Documentacion: [`SEC-2-S-2-ciclo-de-vida-del-token.md`](testing/reportes/SEC-2-S-2-ciclo-de-vida-del-token.md).

## 4. Arquitectura Técnica Obligatoria

El enunciado deja backend, frontend y base de datos agnósticos y solo fija contenedores y migraciones. Se eligió el stack recomendado, razonado en un ADR.

| Exigido | Elegido | Dónde |
|---|---|---|
| Backend (agnóstico) | Java 21 + Spring Boot 3.3.5 | [`backend/`](../backend) |
| Frontend (agnóstico) | React 19 + TypeScript + Vite | [`frontend/`](../frontend) |
| Base de datos (agnóstico) | PostgreSQL 16 | servicio `postgres` |
| Contenedores | Docker + Docker Compose, 14 servicios | [`docker-compose.yml`](../docker-compose.yml) |
| Migraciones | **Flyway**, 7 migraciones `V1`…`V7` | [`backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration/) |


### Documentacion
- Vista de componentes y flujos: [`docs/arquitectura/`](arquitectura/) 
- Elección de stack: [`ADR-001`](decisions/ADR-001-stack-selection.md).

## 5. Full Stack Testing 

 Las **ocho capas** exigidas cumplen y **todas corren en CI**. 307 `@Test` en 33 ficheros. Cobertura del backend medida por JaCoCo en cada ejecución: **92,9 % de líneas, 81,6 % de ramas** (umbral que rompe el build: 80 %).

El detalle capa por capa, con casos y resultados, está en [`docs/testing/guia-de-pruebas.md`](testing/guia-de-pruebas.md) 

### 5.1 Unit Testing

289 tests con JUnit 5 + Mockito + AssertJ sobre las tres cosas que nombra el enunciado: servicios, validaciones y lógica de negocio. Surefire está limitado a `**/*Test.java` y excluye los `*IT`, de modo que la capa unitaria corre sin Docker.

- Diferentes test: `backend/src/test/java/com/inventory/**/*Test.java`
- Configuración de Surefire: [`backend/pom.xml`](../backend/pom.xml).


### 5.2 Integration Testing

**Testcontainers**, con las tres cosas que se piden: base de datos real (PostgreSQL en contenedor), **Keycloak real** (`dasniko/testcontainers-keycloak`, que importa un realm de test con roles, scopes y `scopeMappings` y valida la cadena completa con un token firmado de verdad) e integraciones.

| Fichero | Qué integra |
|---|---|
| `ProductRepositoryIT` (8) | Specification de búsqueda y filtro contra base real, con paginación |
| `AuditIntegrationIT` (4) | Envers escribe las tablas `*_aud` de verdad |
| `StockServiceConcurrencyIT` (3) | Dos movimientos simultáneos no dejan el stock inconsistente |
| `KeycloakAuthIT` (4) | Token real: admin lista (200), viewer no crea (403), sin token (401) |
| `LiveDatabaseIT` (3) | Contra la **base ya desplegada**, no un contenedor efímero |

- Diferentes test: `backend/src/test/java/**/*IT.java` 
- Failsafe y el perfil: `live-db-it` en [`backend/pom.xml`](../backend/pom.xml).


### 5.3 API Testing / Contract Testing

Tres frentes, los tres en CI:

- **Contract testing** — `OpenApiContractTest` valida con swagger-request-validator que las respuestas reales cumplen `docs/api/openapi.yaml`. Si un controlador deja de encajar con el esquema (campo renombrado, tipo cambiado, status no declarado), el build falla.
- **Newman** — la colección Postman corre contra el **stack desplegado**, con token de admin y de viewer: **39 aserciones** sobre CRUD, paginación, búsqueda y los negativos 400/401/403/404/409.
- **Contra el desplegado** — `staging.yml` ejecuta además las cinco comprobaciones del ciclo de vida del token contra un Keycloak vivo.

Vale la pena contarlo en la presentación: ejecutar la colección por primera vez destapó **7 bugs propios** que nunca se habían visto, entre ellos rutas `/api/v1` inexistentes e IDs hardcodeados que borraban datos sembrados.

- OpenApiTest: [`OpenApiContractTest.java`](../backend/src/test/java/com/inventory/common/web/OpenApiContractTest.java) 
- Colección [`inventory-api.postman_collection.json`](postman/inventory-api.postman_collection.json) 
- Newman: `API tests (Newman)` en [`.github/workflows/e2e.yml`](../.github/workflows/e2e.yml).


### 5.4 E2E Testing

**Playwright**, con los seis puntos que nombra el enunciado repartidos en siete specs, todos ejecutándose en CI contra el sistema desplegado:

| Exigido | Spec |
|---|---|
| Flujos completos | `products.spec.ts`, `stock.spec.ts` |
| Navegación | recorrido de las specs anteriores |
| Roles | `roles.spec.ts` |
| Seguridad | `auth.spec.ts` (login, sesión, acceso denegado) |
| Snapshots / Screenshots | `visual.spec.ts` — `toHaveScreenshot()` sobre regiones estables, referencias versionadas |
| Responsive | `responsive.spec.ts` — 375 / 768 / 1440 px, sin desbordamiento horizontal |
| Accesibilidad | `a11y.spec.ts` — axe-core WCAG 2 A/AA, gatea en critical/serious |

Esta capa destapó **dos defectos reales de autenticación**, no fragilidad de los tests: el SPA llamaba `keycloak.login()` sin `scope` (el token llegaba sin permisos y la UI protegida desaparecía), y tras un **F5** `check-sso` perdía los optional scopes y volvía a desaparecer. En producción, el primero habría dejado la app inservible desde el arranque y el segundo tras cualquier refresco.

- e2e: [`e2e/tests/`](../e2e/tests/) 
- Configuración: [`playwright.config.ts`](../e2e/playwright.config.ts) 
- Workflow: [`.github/workflows/e2e.yml`](../.github/workflows/e2e.yml)

### 5.5 Security Testing

Los seis controles :

| Control | Cómo | Resultado |
|---|---|---|
| Escaneo OWASP ZAP | `zap-api-scan` **autenticado** y sembrado con el OpenAPI, con umbral (sin `-I`) | 29 URLs, 118 reglas PASS, **0 WARN** |
| Validación JWT | firma, emisor, expiración | `SecurityIntegrationTest`, `KeycloakJwtConverterTest` |
| Validación de permisos | scope exigido por endpoint, no rol | `SecurityIntegrationTest` + `KeycloakAuthIT` (Keycloak real) |
| Validación de CORS | enforcement real del filtro, no solo la config | `CorsHttpTest` + `CorsProfilesTest` |
| Dependency Check / Snyk | `npm audit` (frontend + e2e) y **OWASP Dependency-Check** (backend, falla en CVSS ≥ 8) | CVEs reales encontradas y corregidas |
| Validación de autenticación | 401 sin token en toda ruta de negocio | `SecurityIntegrationTest` |

Dos matices que conviene defender: el escaneo es **autenticado** (sin token ZAP solo recibiría 401 y "pasaría" sin haber probado nada), y su token dura 3600 s desde un cliente dedicado `inventory-zap` — antes caducaba a los 300 s y el resto de la API se recorría sin autenticar, un falso verde.

- Pasos ZAP: [`.github/workflows/staging.yml`](../.github/workflows/staging.yml) 
- Jenkinks: [`Jenkinsfile`](../Jenkinsfile) 
- Workflow: [`.github/workflows/dependency-scan.yml`](../.github/workflows/dependency-scan.yml).

### 5.6 Performance Testing

**k6** con un perfil combinado que cubre los cinco puntos en una sola ejecución: rampa a 10 VUs → 30 s sostenidos (**load**) → pico de **25 VUs concurrentes** (**stress**) → ramp-down. Los umbrales **hacen fallar el job**: `p(95) < 500 ms` (tiempo de respuesta), `< 1 %` de peticiones fallidas y `> 99 %` de checks en 200. `setup()` obtiene un token compartido y cada VU recorre 7 endpoints de lectura.

- K6 load test: [`scripts/k6/load-test.js`](../scripts/k6/load-test.js) 
- K6 Workflow `Performance tests (k6)` en [`.github/workflows/e2e.yml`](../.github/workflows/e2e.yml).


### 5.7 Data Testing

 Los cinco aspectos  probados **a nivel de esquema**, no de aplicación:

| Aspecto | Cómo |
|---|---|
| Migraciones | 7 migraciones Flyway `V1`…`V7`; los IT levantan el esquema real |
| Seeds | `V5__seed_data.sql`; `DataIntegrityIT` verifica que Flyway los cargó |
| Integridad | FK producto↔movimiento, verificada en `StockServiceConcurrencyIT` |
| Constraints | SKU único y `minimum_stock NOT NULL`, verificados contra la BD |
| Datos duplicados | `DataIntegrityIT` inserta un SKU ya sembrado y comprueba el rechazo |

- DataTest: [`DataIntegrityIT.java`](../backend/src/test/java/com/inventory/product/repository/DataIntegrityIT.java) 
- Migraciones: [`backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration/).

### 5.8 Manual Exploratory Testing

Tres charters, cada uno registrado como issue de GitHub con su reproducción. No fueron decorativos: **cada charter destapó un defecto real que las pruebas automatizadas no veían.**

| Charter | Issue | Qué exploró | Qué encontró |
|---|---|---|---|
| Emisión de scopes OAuth2 | #58 | Buscar escalada de privilegios en el token | **G-6**: Keycloak emitía cualquier scope a cualquiera |
| El pipeline de CI | #59 | Buscar configuración que aprueba sin ejecutar | Un check que terminaba en "No tests to run" en 12 s |
| Arranque del stack desde cero | #60 | `down -v && up` repetido | **P-2b**: `keycloak-init` no era idempotente |

**Total de defectos: 17 issues (#48 `user:manage` aún no protege ningún endpoint — capacidad diferida; #49 Testcontainers sobre Docker Desktop en Windows — limitación de entorno). Ninguno bloquea el pipeline.

 - Guia de pruebas: [`guia-de-pruebas.md`](testing/guia-de-pruebas.md) 
 - Documentacion: [`T-6-issues-de-bug.md`](testing/reportes/T-6-issues-de-bug.md).

## 6 Entornos Obligatorios

 Los tres entornos existen como perfiles de Spring con configuración propia, más un cuarto (`demo`) creado específicamente para la presentación.

| Entorno | Perfil | Qué lo caracteriza |
|---|---|---|
| **Development** | `dev` | Local. Logs en texto legible, CORS a `localhost:3000/5173/4200` |
| **Preview / Staging** | `staging` | Réplica de producción: BD, Keycloak, observabilidad, Compose, variables reales, CI/CD. Logs JSON, CORS al dominio de staging, muestreo 50 % |
| **Production** | `prod` | Logs JSON, CORS al dominio real, sin detalles de error |
| *(demo)* | `demo` | `staging` + CORS local + muestreo 100 %, para la presentación |

las pruebas deberán ejecutarse contra el sistema ya desplegado, no únicamente durante el build se cumple así: `staging.yml` despliega el stack completo y **después** ejecuta smoke tests de API, `LiveDatabaseIT` contra la base desplegada (perfil `live-db-it`, que **falla si no la encuentra** en vez de saltarse en silencio) y el escaneo ZAP autenticado. `e2e.yml` hace lo propio con Playwright, Newman y k6.

- Perfiles: [`backend/src/main/resources/application-{dev,staging,prod,demo}.yml`](../backend/src/main/resources/) 
- Staging: [`staging.yml`](../.github/workflows/staging.yml) 
- E2e: [`e2e.yml`](../.github/workflows/e2e.yml) 
- livedb `live-db-it` en [`backend/pom.xml`](../backend/pom.xml).

## 7. Observabilidad y Telemetría (Obligatorio)

 Las siete piezas del diagrama en el mismo `docker-compose.yml`:

| Pieza exigida | Servicio | Puerto |
|---|---|---|
| Metrics: **Prometheus** | `prometheus` | 9090 |
| Traces: **Tempo** | `tempo` | 3200 |
| Logs: **Loki** | `loki` | 3100 |
| Collector: **Alloy** | `alloy` (OTLP 4317/4318) | 12345 |
| Dashboards: **Grafana** | `grafana` | 3001 |
| Alerting: **Alertmanager** | `alertmanager` | 9093 |
| Instrumentación: **OpenTelemetry** | Micrometer Tracing bridge OTel + exportador OTLP | — |

Se añadieron dos exportadores que el diagrama no nombra pero sin los cuales el dashboard de Infraestructura queda vacío: `node-exporter` (CPU, memoria, disco, red del host) y `postgres-exporter` (conexiones, transacciones, locks).

### 7.1 Requisitos obligatorios — Métricas

 Las siete métricas exigidas: CPU y memoria del host vía node-exporter; JVM (heap, GC, hilos) vía Actuator; latencia, throughput y error rate desde Micrometer con buckets SLO de 50 ms a 2 s; y el pool de base de datos desde HikariCP.

- Prometheus: [`prometheus.yml`](../observability/prometheus/prometheus.yml) 
- Configuración de Micrometer: `application.yml`.

En Prometheus (http://localhost:9090 → *Status → Targets*): todos los targets `UP`.

### 7.2 Requisitos obligatorios — Logs

Los **seis campos** que se piden viajan en el MDC y llegan a Loki como campos de primer nivel: `traceId`, `spanId`, `correlationId`, `level`, `user` y `endpoint`. Los dos filtros propios son los que aportan `correlationId` (aceptando el de cabecera o generándolo) y `user` (desde el JWT).

- CorrelationIDFilter: [`CorrelationIdFilter.java`](../backend/src/main/java/com/inventory/common/observability/CorrelationIdFilter.java) 
- UserMdcFilter: [`AuthenticatedUserMdcFilter.java`](../backend/src/main/java/com/inventory/common/observability/AuthenticatedUserMdcFilter.java)  
- Configuracion: [`config.alloy`](../observability/alloy/config.alloy) 
- evidencia: [`OBS-4-logs-loki.md`](testing/reportes/OBS-4-logs-loki.md).

En Grafana → **2 · Aplicación** → panel *Logs correlacionados*: filtrar por `user="inv_admin"` o por `endpoint`. Ese filtro **solo funciona con perfil `demo`/`staging`/`prod`** — es la razón de existir del perfil `demo`.

### 7.3 Requisitos obligatorios — Trazas Distribuidas

Los cuatro puntos: request tracing (Micrometer bridge OTel), **database tracing** (`datasource-micrometer`, porque el bridge traza HTTP y seguridad pero no las consultas, y el enunciado pide explícitamente trazado de base de datos), llamadas externas y errores distribuidos. Este último fue un defecto encontrado y corregido: los 500 no llegaban a Tempo.

- Dependencias: [`backend/pom.xml`](../backend/pom.xml) (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `datasource-micrometer-spring-boot`) 
- ruta OTLP: en [`observability/alloy/config.alloy`](../observability/alloy/config.alloy).

#### Prueba

1. Hacer una petición y quedarse con el `traceId`:
   ```bash
   curl -s http://localhost:8080/products -H "Authorization: Bearer $TOKEN" -o /dev/null
   docker compose logs backend --tail 1 | jq -r '.traceId'
   ```
2. Grafana → *Explore* → datasource **Tempo** → pegar el `traceId`. Se ve el span HTTP y **debajo los spans de las consultas JDBC**, que es lo que demuestra el database tracing.
3. Para errores distribuidos, provocar un 500 o un 404 de dominio y buscar su traza.

### 7.4 Requisitos obligatorios — Dashboards

 Los **cuatro** dashboards  provisionados como código :

| # | Dashboard | Paneles destacados |
|---|---|---|
| 1 | **Infraestructura** | CPU, memoria, load, disco, red del host; conexiones, tamaño y locks de PostgreSQL |
| 2 | **Aplicación** | Throughput, latencia p95 (global y por endpoint), error rate 5xx, heap JVM, pool HikariCP, GC e hilos, logs correlacionados |
| 3 | **Negocio** | Productos bajo mínimo, movimientos y unidades por tipo, cruces de mínimo por SKU |
| 4 | **Seguridad** | Fallos de autenticación (401) y autorización (403) separados, top de endpoints rechazados, alertas activas, eventos de Keycloak |

- Dashboards: [`observability/grafana/provisioning/dashboards/`](../observability/grafana/provisioning/) (4 JSON + `provider.yml`) · datasources en `provisioning/datasources/` 
- Capturas de evidencia en [`docs/testing/capturas/`](testing/capturas/).

#### Prueba

http://localhost:3001 (admin / `.env`) → *Dashboards*. Los cuatro aparecen ya provisionados, sin importar nada.


### 7.5 Requisitos obligatorios — Alertas

Las **cinco alertas** exigidas +  una de negocio:

| Exigida por el PDF | Regla |
|---|---|
| Alto consumo de CPU | `AltoConsumoCPU` |
| Servicios caídos | `ServicioCaido` |
| Error rate elevado | `ErrorRateElevado` |
| Latencia alta | `LatenciaAlta` |
| Fallos de autenticación | `FallosDeAutenticacion` |
| De negocio | `ProductosBajoMinimo` |

- Alertas: [`alerts.yml`](../observability/prometheus/rules/alerts.yml) 
- Enrutado: [`alertmanager.yml`](../observability/alertmanager/alertmanager.yml) 
- Evidencia en [`/OBS-5-alertas.md`](testing/reportes/OBS-5-alertas.md) 
- Capturas `05-prometheus-alertas.png`, `06-alertmanager.png`.

#### Prueba

Ver las seis reglas cargadas: http://localhost:9090/alerts

**Disparar una en vivo** (la más rápida y visual es `FallosDeAutenticacion`):

## 8. Calidad de Código

**SonarCloud** analiza en cada ejecución de CI y mide las **cinco** métricas que exige el enunciado: Coverage, Bugs, Vulnerabilities, Code smells y Duplicación. Los badges del README no son estáticos: los sirve SonarCloud desde el último análisis, así que no pueden quedarse desfasados.

El análisis va **después** de `verify` a propósito, porque es `verify` quien genera `target/site/jacoco/jacoco.xml`; sin ese informe SonarCloud reportaría 0 % de cobertura sobre un proyecto que está por encima del 90 %.

Complementos locales: **JaCoCo** con umbral que rompe el build (80 % de líneas y ramas) y **Spotless** con Google Java Format en fase `validate` — es decir, el formato no es una sugerencia, un fichero mal formateado no compila en CI.

**Estado actual:** 92,9 % de líneas · 81,6 % de ramas (backend). El frontend está en **6,3 %** y es el hueco de calidad conocido, declarado como tal en el plan en vez de disimulado. Vale la pena saber que el informe de frontend marcaba **100 %** hasta que se configuró `coverage.include` en vitest: solo medía las 14 sentencias que los tests importaban.

- Configuración de Sonar, JaCoCo y Spotless: [`backend/pom.xml`](../backend/pom.xml) 
- IT: `integration-test` de [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 
- Etapa Jenkins: [`Jenkinsfile`](../Jenkinsfile) etapa *Quality Gate — SonarCloud* 
- Refactor de code smells documentado: [`docs/testing/reportes/Q-5-code-smells.md`](testing/reportes/Q-5-code-smells.md).

### Prueba

abrir el proyecto en SonarCloud desde cualquiera de los seis badges del [`README.md`](../README.md) — Quality Gate, Coverage, Bugs, Vulnerabilities, Code Smells y Duplicated Lines. Las cinco métricas exigidas están ahí, en una sola pantalla.

## 9. DevSecOps y CI/CD

### 9.1 GitHub Actions (Obligatorio)

Seis workflows. Los cinco puntos—Build, Tests, Security scans, Coverage, Docker build— están cubiertos:

| Workflow | Se dispara | Qué hace |
|---|---|---|
| [`ci.yml`](../.github/workflows/ci.yml) | PR y push a `main` | 3 jobs: unit tests + contract; frontend lint/build/tests/coverage; integración con Testcontainers + gate JaCoCo + SonarCloud |
| [`e2e.yml`](../.github/workflows/e2e.yml) | PR y manual | Despliega el stack demo y corre **Playwright + Newman + k6** contra él |
| [`staging.yml`](../.github/workflows/staging.yml) | push a `main`/`develop`, manual | Despliega, API smoke, `LiveDatabaseIT` contra base viva, **ZAP autenticado** |
| [`dependency-scan.yml`](../.github/workflows/dependency-scan.yml) | programado y push | `npm audit` + **OWASP Dependency-Check** (falla en CVSS ≥ 8) |
| [`production.yml`](../.github/workflows/production.yml) | tags `v*.*.*` | Verifica, construye imagen de producción y publica GitHub Release |
| [`promote-to-staging.yml`](../.github/workflows/promote-to-staging.yml) | push | Promoción de rama a `staging` |

Los cuatro checks de `ci.yml` son **obligatorios** y `main` exige revisión aprobatoria.

#### Prueba

Abrir la pestaña **Actions** del repositorio y enseñar, en este orden:
1. Un run verde de `ci.yml` → los 3 jobs y los artefactos (surefire, JaCoCo, cobertura de frontend).
2. Un run de `e2e.yml` → artefactos de Playwright, Newman y el resumen JSON de k6.
3. Un run de `staging.yml` → el informe ZAP, y el orden deploy → tests.
4. Un PR cualquiera → los checks obligatorios bloqueando el merge.


### 9.2 Jenkins (Obligatorio)

Pipeline declarativo de **11 etapas** sobre un Jenkins **configurado como código**: plugins, credenciales, tool de JDK y el propio job viven en `docker/jenkins/`, no dentro del volumen del contenedor. Eso significa que el Jenkins de la demo se levanta desde cero reproducible, no es una instalación artesanal.

Etapas: Checkout → Build → Unit Tests → Integration Tests → Quality Gate (SonarCloud) → Package → Docker Build → Deploy Stack → API Smoke Tests → E2E (Playwright) → Security Scan (ZAP).



- Jenkins: [`Jenkinsfile`](../Jenkinsfile) 
- Docker: [`docker/docker-compose.jenkins.yml`](../docker/docker-compose.jenkins.yml) 
- Configuración como código: [`docker/jenkins/`](../docker/jenkins/).

#### Prueba

```bash
export JENKINS_ADMIN_ID=admin
export JENKINS_ADMIN_PASSWORD=...
export JENKINS_ENV_FILE_B64=$(base64 -w0 .env)
docker compose -f docker/docker-compose.jenkins.yml up -d --build
```

### 9.3 Pipeline Obligatorio

Las diez etapas y dónde vive cada una:

| Etapa exigida | GitHub Actions | Jenkins |
|---|---|---|
| Checkout | todos los workflows | `Checkout` |
| Build | `ci.yml` · `production.yml` | `Build` |
| Unit tests | `ci.yml` job `unit-tests` | `Unit Tests` |
| Integration tests | `ci.yml` job `integration-test` | `Integration Tests` |
| API tests | `e2e.yml` (Newman) · `staging.yml` (smoke) | `API Smoke Tests` |
| E2E tests | `e2e.yml` (Playwright) | `E2E Tests — Playwright` |
| Security scan | `staging.yml` (ZAP) · `dependency-scan.yml` | `Security Scan — ZAP` |
| Quality gates | `ci.yml` (SonarCloud + JaCoCo 80 %) | `Quality Gate — SonarCloud` |
| Docker build | `e2e.yml` · `production.yml` | `Docker Build` |
| Deployment | `staging.yml` · `production.yml` | `Deploy — Stack` |

**Las diez están cubiertas en ambos pipelines.**

#### Prueba

Esta tabla es la prueba: proyectarla al lado del Stage View de Jenkins y de la lista de jobs de Actions.

### 9.4 Repositorio GitHub

Repositorio público con README (badges vivos, stack, arranque, API documentada, convención de ramas), issues con reproducción, pull requests con revisión y estrategia de ramas por prefijo (`feat/`, `fix/`, `docs/`, `test/`, `ci/`, `chore/`).

- Repositorio: https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial 
- Readme: [`README.md`](../README.md) 
- Contribucion: [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## 10. Documentación

### a. Documentación de Requisitos

**22 requisitos funcionales y 24 no funcionales**, y cada uno con tres cosas que no suelen aparecer juntas: la **cita literal del PDF** que lo origina, el **`fichero:línea`** que lo implementa y la **prueba** que lo verifica. Eso convierte el documento en una matriz de trazabilidad.

- Requisitos funcionales: [`requisitos-funcionales.md`](requisitos/requisitos-funcionales.md) 
- Requisitos no funcionales: [`requisitos-no-funcionales.md`](requisitos/requisitos-no-funcionales.md).


### b. Documentación Técnica

Diagramas de arquitectura y vista de componentes, desglose backend/frontend, guía de instalación y manual de mantenimiento (operación, respaldo y restauración). Añadidos cuatro **ADR** que registran las decisiones difíciles con sus alternativas descartadas.

- Arquitectura: [`docs/arquitectura/`](arquitectura/) 
- Manual de mantenimiento: [manual-mantenimiento.md`](operacion/manual-mantenimiento.md) 
- Documentacion decisiones: [`docs/decisions/`](decisions/) (ADR-001 a ADR-004).

### c. Guía de Pruebas

Documenta las ocho capas con sus casos, resultados y defectos, más una sección de "cómo ejecutar cada suite" y los 17 defectos con su estado.

- Guia de Pruebas: [`docs/testing/guia-de-pruebas.md`](testing/guia-de-pruebas.md) 
- informes: [`docs/testing/reportes/`](testing/reportes/)  
- Capturas:  [`docs/testing/capturas/`](testing/capturas/) 


## 12. Buenas Prácticas Obligatorias

| Exigido | Cómo se resolvió | Dónde |
|---|---|---|
| **Conventional Commits** | commitlint en hook de Husky: un mensaje mal formado **no llega a commitearse** | [`commitlint.config.js`](../commitlint.config.js), [`.husky/`](../.husky/) |
| **Pull Requests** | Todo entra por PR; ninguna rama de trabajo empuja a `main` | Pestaña PRs |
| **Code Reviews** | `main` exige revisión aprobatoria | Settings → Branches |
| **Branch protection** | 4 checks obligatorios + revisión | Settings → Branches |
| **Secrets management** | Secrets de GitHub Actions y credenciales de Jenkins; nada en el repo | [`docs/GITHUB_SECRETS.md`](GITHUB_SECRETS.md), `withCredentials` en el `Jenkinsfile` |
| **Variables de entorno** | Todo por `.env`, con `.env.example` documentado y `.env` en `.gitignore` | [`.env.example`](../.env.example) |
| **No hardcoded credentials** | Ni el token de Sonar ni las contraseñas de Keycloak aparecen en el código; el `JWT_SECRET` muerto se eliminó (S-4b) | verificado por Sonar y por `dependency-scan.yml` |

### Prueba

Commitlint, en vivo:

```bash
git commit --allow-empty -m "esto no cumple"     # rechazado por el hook
git commit --allow-empty -m "docs: mensaje válido"
```

