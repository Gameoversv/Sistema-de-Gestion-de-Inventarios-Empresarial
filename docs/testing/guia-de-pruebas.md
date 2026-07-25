# Guía de Pruebas

Cubre el entregable *"Guía de Pruebas: documentar los casos de prueba, los resultados y cualquier defecto encontrado"*.

El enunciado exige **ocho capas** de testing. Esta guía dice, capa por capa, qué se prueba, con qué, dónde vive, qué resultado da hoy y qué falta. Los defectos encontrados están al final, cada uno como issue de GitHub con reproducción.

- Casos y resultados por capa → secciones 1-8
- Cómo ejecutar cada suite → [sección 9](#9-cómo-ejecutar-las-pruebas)
- Defectos → [sección 10](#10-defectos-encontrados)
- El detalle verificado de cada hallazgo vive en [`reportes/`](reportes/)

---

## Resumen

| Capa | Estado | Evidencia |
|---|---|---|
| 1. Unit | **Cumple** | 289 tests unitarios |
| 2. Integration | **Cumple** | Testcontainers con base real **y Keycloak real** (`KeycloakAuthIT`, TEST-1) |
| 3. API / Contract | **Cumple** | Contract test contra `openapi.yaml` (TEST-2) + colección Postman con Newman en CI (TEST-3, 39 aserciones) |
| 4. E2E | **Cumple** | 3 specs (12 casos) de Playwright, **12/12 en CI** por `e2e.yml` (C-1/TEST-7); faltan snapshots y responsive como mejora |
| 5. Security | **Cumple** | ZAP autenticado (TEST-10), enforcement CORS (TEST-11), npm audit + OWASP Dependency-Check (T-5) |
| 6. Performance | **Cumple** | k6 en CI (T-3): load + stress, `p(95)<500ms` |
| 7. Data | **Cumple** | Flyway + seeds verificados, `DataIntegrityIT` (duplicados y constraints a nivel BD) |
| 8. Exploratory | **Cumple** | 3 charters, 15 bugs con reproducción |

**307 `@Test` en 33 ficheros** (más los 4 de `KeycloakAuthIT`). Cobertura del backend: **85,0 % de ramas, 92,7 % de líneas** (JaCoCo en CI, umbral 80 %). Frontend: **9,3 %** de líneas — el hueco de calidad conocido.

**Las ocho capas cumplen** y se ejecutan en CI, con las mejoras de E2E (snapshots, accesibilidad, responsive) y de Security (TEST-10b) también cerradas.

---

## 1. Unit Testing — **Cumple**

> *"Debe incluir: Servicios, Validaciones, Lógica de negocio"* · Herramientas: JUnit 5, Mockito, AssertJ.

289 tests unitarios sobre las tres cosas que el enunciado nombra. Distribución real por área:

| Área | Tests | Ficheros que más cargan |
|---|---|---|
| Productos y categorías | ~110 | `ProductControllerTest` (24), `ProductServiceImplExtendedTest` (22), `CategoryControllerTest` (20) |
| Reportes | ~44 | `ReportControllerExtendedTest` (14), `ReportServiceExtendedTest` (12) |
| Stock | ~40 | `StockControllerTest` (12), `StockServiceTest` (11), `StockMovementSpecTest` (10) |
| Auditoría | ~34 | `UnifiedAuditServiceTest` (15), `StockAuditServiceTest` (11) |
| Seguridad y observabilidad | ~27 | `GlobalExceptionHandlerTest` (11), `KeycloakJwtConverterTest` (10) |

**Casos representativos:**

| Qué prueba | Dónde |
|---|---|
| Servicio de producto: alta, edición, soft delete, búsqueda | `ProductServiceTest`, `ProductServiceImplExtendedTest` |
| Validación de DTO: SKU obligatorio, precio ≥ 0, campos con límite | `ProductCreateRequestValidationTest` (12), `CategoryCreateRequestValidationTest` (6) |
| Lógica de negocio del stock: antes/después, tipo de movimiento | `StockServiceTest` |
| El converter JWT: roles→scopes, intersección, denegar sin rol | `KeycloakJwtConverterTest` (10) |
| Mapeo entidad↔DTO sin pérdida de campos | `ProductMapperTest` (12) |
| Excepciones de dominio → `ProblemDetail`, sin filtrar internos | `GlobalExceptionHandlerTest` (11) |

`KeycloakJwtConverterTest` importa más que su tamaño: verifica en unitario el control de acceso efectivo del sistema (RNF-02), incluido que un rol desconocido no reciba ninguna autoridad.

---

## 2. Integration Testing — **Cumple**

> *"Obligatorio utilizar: Testcontainers. Debe probarse: Base de datos real, Keycloak, Integraciones"*

18 tests en 4 ficheros `*IT`, con Testcontainers levantando **PostgreSQL real**:

| Fichero | Qué integra |
|---|---|
| `ProductRepositoryIT` (8) | Specification de búsqueda/filtro contra base real, con paginación |
| `AuditIntegrationIT` (4) | Envers escribe las tablas `*_aud` de verdad al modificar y borrar |
| `StockServiceConcurrencyIT` (3) | Dos movimientos simultáneos no dejan el stock inconsistente |
| `LiveDatabaseIT` (3) | Contra la **base ya desplegada** (perfil `live-db-it`), no un contenedor efímero |

`LiveDatabaseIT` cubre lo que el enunciado pide en Entornos: pruebas contra el sistema desplegado, no solo durante el build. **Falla si no encuentra la base**, en vez de saltarse silenciosamente (ENV-1).

**Keycloak real — TEST-1, cumplido.** `KeycloakAuthIT` levanta un Keycloak en contenedor (`dasniko/testcontainers-keycloak`), importa un realm de test con roles, scopes y `scopeMappings`, y valida la cadena completa con un token firmado de verdad: admin lista (200), viewer no crea (403), sin token (401) y la reverificación de G-8 a nivel IT (un viewer no obtiene `product:manage`). Verificado en el job `integration-test` de CI; no corre en local por C-4.

**Aviso de entorno — C-4.** Estos IT **pasan en los runners Linux de GitHub Actions** en cada PR, pero **no arrancan sobre Docker Desktop en Windows**: terminan en `Could not find a valid Docker environment` aunque Docker esté levantado y el socket montado. Es el proxy del socket de Docker Desktop, no la configuración. Consecuencia: las etapas de Jenkins a partir de `Integration Tests` solo se validan en un agente Linux (issue #49).

---

## 3. API / Contract Testing — **Cumple**

> *"Validación de endpoints, contratos OpenAPI, status codes y payloads"*

Tres frentes, los tres en CI:

- **Contract testing (TEST-2).** `OpenApiContractTest` valida que las respuestas reales cumplen el contrato de `docs/api/openapi.yaml` con swagger-request-validator sobre MockMvc. Si un controlador deja de encajar con el esquema (campo renombrado, tipo cambiado, status no declarado), falla. Corre en el job rápido, sin stack.
- **Newman (TEST-3).** La colección Postman de `docs/postman/` corre con Newman contra el stack desplegado en `e2e.yml`, con token de admin y de viewer: **39 aserciones** sobre CRUD de productos y categorías, paginación, búsqueda, y los negativos 400/401/403/404/409.
- **Contra el desplegado.** `staging.yml` ejecuta además las 5 comprobaciones del ciclo de vida del token contra un Keycloak vivo (`grant_type=refresh_token`; ver [informe SEC-2/S-2](reportes/SEC-2-S-2-ciclo-de-vida-del-token.md)). Los controladores tienen cobertura unitaria con MockMvc.

Ejecutar la colección por primera vez destapó **7 bugs propios** (nunca se había probado): apuntaba a `/api/v1` inexistente, `/stock` sin prefijo, IDs hardcodeados que borraban datos sembrados, un soft-delete que esperaba 200+body en vez de 204, un nombre de categoría ya sembrado, un SKU de prueba inexistente y una aserción de Content-Type mal escrita.

---

## 4. E2E Testing — **Cumple**

> *"Snapshots/Screenshots, Flujos completos, Navegación, Roles, Seguridad y Responsive"* · Playwright.

Tres specs escritos en `e2e/tests/`:

| Spec | Cubre |
|---|---|
| `auth.spec.ts` | Login, sesión, acceso denegado |
| `products.spec.ts` | Flujo de productos: listado, alta, edición |
| `stock.spec.ts` | Registro de movimiento y su reflejo |

**El pipeline los ejecuta (C-1 / TEST-7).** `e2e.yml` despliega el stack con perfil demo y corre los tres specs contra el sistema desplegado en cada PR, subiendo el informe de Playwright como artefacto. Era la única de las 10 etapas del pipeline que faltaba en Actions.

Esta etapa destapó **dos defectos reales de autenticación**, no fragilidad de los specs:

1. El SPA llamaba `keycloak.login()` sin `scope`, así que el token no traía los permisos (son *optional scopes*) y `PermissionGuard` ocultaba toda la interfaz protegida. Corregido pidiendo los siete scopes en el login; los `scope-mappings` de G-8 los recortan por rol.
2. Tras un **refresco de página**, `check-sso` obtenía un token silencioso sin los optional scopes, y la interfaz protegida volvía a desaparecer. Corregido en `AuthContext`: si el token no trae scopes de negocio, se reobtiene con un login silencioso (sesión SSO activa), con guard anti-bucle.

En producción, ambos habrían dejado la app inservible: el primero desde el arranque, el segundo tras cualquier F5.

La capa cubre además, toda en CI, lo que el enunciado nombra —snapshots, roles, seguridad, responsive—:
- **Regresión visual (TEST-8)** — `visual.spec.ts` compara con `toHaveScreenshot()` regiones estables (sidebar y formulario de alta), con referencias versionadas generadas en CI; un cambio de UI por encima de la tolerancia tumba el job.
- **Accesibilidad (D-4)** — `a11y.spec.ts` corre axe-core (WCAG 2 A/AA) sobre dashboard, productos y stock, gateando en violaciones critical/serious. Destapó fallos reales: selects e inputs sin nombre accesible, corregidos con `aria-label`. La regla de contraste queda excluida y anotada como ajuste de diseño pendiente.
- **Responsive (TEST-9)** — `responsive.spec.ts` comprueba que dashboard y productos no desbordan horizontalmente en **375, 768 y 1440 px**.
- **Roles y seguridad** ya los ejercitan `auth.spec.ts` y el resto: login/logout, acceso denegado, y la gestión gated por permisos.

---

## 5. Security Testing — **Cumple**

> *"Escaneo OWASP ZAP, Validación JWT, Validación de permisos, Validación de CORS, OWASP Dependency Check / Snyk, Validación de autenticación"*

| Control | Cómo | Resultado |
|---|---|---|
| Escaneo ZAP | `zap-api-scan` en `staging.yml`, **autenticado** y sembrado con el OpenAPI, con umbral (sin `-I`) | 29 URLs, 118 reglas PASS, **0 WARN** (TEST-10) |
| Validación JWT | firma, emisor y expiración | `SecurityIntegrationTest`, `KeycloakJwtConverterTest` |
| Validación de permisos | scope exigido por endpoint, no rol | `SecurityIntegrationTest`; y a nivel IT contra Keycloak real (`KeycloakAuthIT`) |
| Validación de autenticación | 401 sin token en toda ruta de negocio | `SecurityIntegrationTest` |
| **CORS (TEST-11)** | enforcement real del filtro: preflight de origen permitido recibe `Access-Control-Allow-Origin`, uno no permitido no | `CorsHttpTest` (runtime) + `CorsProfilesTest` (config por perfil) |
| **Dependencias (T-5)** | `dependency-scan.yml`: npm audit (frontend + e2e) y OWASP Dependency-Check (backend, falla en CVSS≥8) | El audit destapó CVEs reales; los que tenían fix se corrigieron |

El escaneo de dependencias no fue un trámite: `npm audit` encontró CVEs altas reales (brace-expansion DoS, js-yaml, postcss path traversal), que se corrigieron actualizando el lockfile. Las que quedan son de react-router 7.x sin fix publicado (open redirect en `<Link>` y su modo RSC, que la app no usa); se gatea en critical y se documenta.

**TEST-10b — cerrado.** El token del escaneo ZAP caducaba a los 300 s; si el escaneo activo duraba más, el resto de la API se recorría sin autenticar. Ahora `staging.yml` obtiene el token de un cliente dedicado `inventory-zap` con `access.token.lifespan=3600`. Un paso de `e2e.yml` verifica, contra el mismo realm, que ese cliente emite un token de ~3600 s (issue #46).

---

## 6. Performance Testing — **Cumple**

> *"Stress testing, Load testing, Concurrent users, Tiempo de respuesta y Throughput"* · JMeter y/o k6.

`scripts/k6/load-test.js` ejercita **load, stress y usuarios concurrentes** contra el sistema desplegado en `e2e.yml`, con umbrales que hacen fallar el job:

- **Tiempo de respuesta:** `http_req_duration` con `p(95)<500ms`.
- **Throughput / concurrencia:** perfil por etapas — rampa a 10 VUs, 30 s sostenidos, pico de **25 VUs concurrentes** (stress), ramp-down.
- **Fiabilidad bajo carga:** `<1%` de peticiones fallidas y `>99%` de checks en 200.

`setup()` obtiene un token compartido; cada VU recorre 7 endpoints de lectura (productos con paginación y búsqueda, categorías, reportes, stock). El resumen JSON se sube como artefacto. La instrumentación del backend (buckets SLO `50ms…2s`, histograma de percentiles) da las series que respaldan la medición.

---

## 7. Data Testing — **Cumple**

> *"Migraciones, Integridad de datos, Datos duplicados, Constraints y Seeds"*

| Aspecto | Estado |
|---|---|
| Migraciones | **Cumple** — 7 migraciones Flyway (`V1`…`V7`); los IT levantan el esquema real |
| Seeds | **Cumple** — `V5__seed_data.sql`; `DataIntegrityIT` verifica que Flyway los cargó |
| Integridad | **Cumple** — FK producto↔movimiento en `StockServiceConcurrencyIT` |
| Constraints | **Cumple** — SKU único y `minimum_stock NOT NULL` verificados a nivel BD en `DataIntegrityIT` |
| **Datos duplicados** | **Cumple** — `DataIntegrityIT` inserta un SKU ya sembrado y comprueba el rechazo por unicidad; `ProductRepositoryIT` cubre el duplicado directo |

`DataIntegrityIT` prueba las restricciones **en el esquema**, no en la capa de aplicación: `minimum_stock` no lleva `@NotNull` en la entidad (solo `@Min`), así que Bean Validation lo deja pasar y es la columna `NOT NULL` la que lo rechaza — justo lo que hay que verificar.

---

## 8. Manual Exploratory Testing — **Cumple**

> *"Exploratory charters, Bugs encontrados y Escenarios explorados"*

Tres charters, registrados como issues de GitHub con su reproducción:

| Charter | Issue | Qué exploró | Qué encontró |
|---|---|---|---|
| Emisión de scopes OAuth2 | #58 | Buscar escalada de privilegios en el token | **G-6**: Keycloak emite cualquier scope a cualquiera |
| El pipeline de CI | #59 | Buscar configuración que aprueba sin ejecutar | El check que terminaba en "No tests to run" en 12 s |
| Arranque del stack desde cero | #60 | `down -v && up` repetido | **P-2b**: `keycloak-init` no es idempotente |

Los charters no son decorativos: cada uno destapó un defecto real que las pruebas automatizadas no veían. La escalada de scopes (G-6) es hoy la razón de que el control de acceso viva en el backend.

---

## 9. Cómo ejecutar las pruebas

| Suite | Comando | Dónde corre en CI |
|---|---|---|
| Unit + cobertura | `cd backend && ./mvnw test` | `ci.yml` → job `unit-tests` |
| Integración (Testcontainers) | `cd backend && ./mvnw verify` | `ci.yml` → job `integration-test` (runner Linux) |
| Frontend unit + cobertura | `cd frontend && npm test` | `ci.yml` → job `frontend` |
| Contract (OpenAPI) | `cd backend && ./mvnw -Dtest=OpenApiContractTest test` | `ci.yml` → job `unit-tests` (TEST-2) |
| E2E | `cd e2e && npx playwright test --grep-invert @visual` | `e2e.yml` (C-1/TEST-7), contra el stack desplegado |
| Accesibilidad (D-4) | incluida en el run de Playwright | `e2e.yml` — axe-core sobre las páginas |
| Regresión visual (TEST-8) | `npx playwright test --grep @visual` | `e2e.yml` — snapshots de regiones estables |
| Responsive (TEST-9) | incluida en el run de Playwright | `e2e.yml` — 375 / 768 / 1440 px |
| API (Newman) | `newman run docs/postman/...json` | `e2e.yml` (TEST-3), contra el stack desplegado |
| Performance (k6) | `k6 run scripts/k6/load-test.js` | `e2e.yml` (T-3), contra el stack desplegado |
| Dependency scan | `cd frontend && npm audit` | `dependency-scan.yml` (T-5): npm audit + OWASP DC |
| CORS (TEST-11) | `cd backend && ./mvnw -Dtest=CorsHttpTest test` | `ci.yml` → job `unit-tests` |
| API + Security contra el desplegado | — | `staging.yml` tras el deploy |
| Análisis estático | — | SonarCloud en cada run |

> Los IT **no arrancan sobre Docker Desktop en Windows** (C-4). En local, en Windows, `./mvnw test` (solo unit) sí corre; para los IT hace falta un entorno Linux o el propio CI.

**Reportes generados:** surefire (unit), failsafe (IT), JaCoCo (cobertura), cobertura de frontend, informe de ZAP, informe de Playwright, el JUnit de Newman, el resumen JSON de k6 y el informe de OWASP Dependency-Check, todos como artefactos de CI.

---

## 10. Defectos encontrados

17 bugs registrados como issues, con reproducción en el cuerpo. **15 corregidos, 2 abiertos.** Los corregidos se registraron cerrados, cada uno enlazando el PR que lo arregló y declarando que la issue se abrió después del arreglo, para dejar el rastro (T-6, [informe](reportes/T-6-issues-de-bug.md)). Los dos que siguen abiertos: **#48** (`user:manage` no protege nada, A-2, diferido a la Ola 8) y **#49** (Testcontainers no arranca sobre Docker Desktop en Windows, C-4, limitación de entorno; pasan en los runners Linux).

### Abiertos (2)

| # | Defecto | Severidad | Tarea |
|---|---|---|---|
| [#48](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/48) | `user:manage` no protege ningún endpoint | Alto | A-2 — diferido a la Ola 8 |
| [#49](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/49) | Testcontainers no arranca sobre Docker Desktop en Windows | Bajo (entorno) | C-4 — pasan en los runners Linux |

Ninguno bloquea el pipeline: #48 es una capacidad diferida (el permiso se emite pero aún no protege un endpoint) y #49 es una limitación de Docker Desktop en Windows, no del código.

### Corregidos (15)

De la sesión de exploración y arreglos: escalada por primer-rol-gana (#50), fallback de scopes (#51), el check de CI que aprobaba en 12 s (#52), el badge placeholder (#53), la cobertura de frontend que informaba 100 % midiendo 14 sentencias (#54), Spotless desactivado (#55), el README que documentaba una API inexistente (#56) y el CORS de `staging` (#57). De montar los E2E en CI: el SPA sin scopes en el login (#69) y `check-sso` que los perdía al refrescar (#70). Y la deuda de la Ola 7, cerrada al mergearse: la escalada de scopes de raíz en el IdP (#43, G-8), `AuthContext` primer-rol-gana (#44, G-3a), `keycloak-init` no idempotente (#45, P-2b), el `JWT_SECRET` muerto (#47, S-4b) y el token del escaneo ZAP (#46, TEST-10b). Cada uno con su PR enlazado.

---

## Estado de la pirámide

**Las ocho capas del enunciado cumplen y corren en CI**, y las mejoras identificadas están cerradas: E2E con snapshots (TEST-8), accesibilidad (D-4) y responsive (TEST-9); Security con el cliente ZAP de token largo (TEST-10b). La pirámide de testing está completa.

Trazabilidad completa de cada identificador en el [plan de ejecución](../PLAN_EJECUCION.md), §4.3.
