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
| 3. API / Contract | Parcial | API tests en `staging.yml`; Postman y RestAssured sin CI |
| 4. E2E | **Cumple** | 3 specs (12 casos) de Playwright, **12/12 en CI** por `e2e.yml` (C-1/TEST-7); faltan snapshots y responsive como mejora |
| 5. Security | Parcial | ZAP autenticado con umbral; faltan Dependency Check y CORS |
| 6. Performance | **Cero** | sin una sola prueba de carga |
| 7. Data | Parcial | migraciones y seeds; faltan duplicados y constraints |
| 8. Exploratory | **Cumple** | 3 charters, 15 bugs con reproducción |

**307 `@Test` en 33 ficheros** (más los 4 de `KeycloakAuthIT`). Cobertura del backend: **85,0 % de ramas, 92,7 % de líneas** (JaCoCo en CI, umbral 80 %). Frontend: **9,3 %** de líneas — el hueco de calidad conocido.

**Cuatro capas completas** (Unit, Integration, E2E, Exploratory), tres parciales (API/Contract, Security, Data) y una a cero (Performance). Los dos obligatorios que faltaban —Testcontainers con Keycloak (TEST-1) y E2E en CI (C-1/TEST-7)— ya están cerrados; lo que queda son pruebas por escribir, no pruebas escritas sin ejecutar.

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

## 3. API / Contract Testing — **Parcial**

> *"Validación de endpoints, contratos OpenAPI, status codes y payloads"*

**Lo que sí corre:** `staging.yml` ejecuta API tests **contra el sistema desplegado**, incluidas 5 comprobaciones del ciclo de vida del token contra un Keycloak vivo (`grant_type=refresh_token`, y el negativo: refresh inválido → 400). Ver [informe SEC-2/S-2](reportes/SEC-2-S-2-ciclo-de-vida-del-token.md).

Los controladores están cubiertos en unitario con MockMvc, verificando status codes y payloads: `ProductControllerTest`, `StockControllerTest`, `ReportControllerTest`, `AuditControllerTest`.

**Qué falta:**
- **TEST-3** — la colección Postman de `docs/postman/` no corre en CI con Newman.
- **TEST-2** — RestAssured para validar el contrato OpenAPI generado (`docs/api/openapi.yaml`) contra la implementación.

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

**Qué falta dentro de la capa:**
- **TEST-8** — `toHaveScreenshot()` en dashboard, productos y stock (snapshots).
- **TEST-9** — responsive a 375 / 768 / 1440 px.
- **D-4** — `@axe-core/playwright` para accesibilidad automatizada.

---

## 5. Security Testing — **Parcial**

> *"Escaneo OWASP ZAP, Validación JWT, Validación de permisos, Validación de CORS, OWASP Dependency Check / Snyk, Validación de autenticación"*

**Lo que cumple:**

| Control | Cómo | Resultado |
|---|---|---|
| Escaneo ZAP | `zap-api-scan` en `staging.yml`, **autenticado** y sembrado con el OpenAPI, con umbral (sin `-I`) | Local: 29 URLs, 118 reglas PASS, **0 WARN** (TEST-10) |
| Validación JWT | firma, emisor y expiración | `SecurityIntegrationTest`, `KeycloakJwtConverterTest` |
| Validación de permisos | scope exigido por endpoint, no rol | `SecurityIntegrationTest` |
| Validación de autenticación | 401 sin token en toda ruta de negocio | `SecurityIntegrationTest` |

**Qué falta:**
- **T-5** — OWASP Dependency Check y `npm audit`/Snyk en CI. Ningún escaneo de CVEs de dependencias hoy.
- **TEST-11** — test de CORS de extremo a extremo por perfil (hoy solo `CorsProfilesTest` en unitario).
- **TEST-10b** — el token del escaneo caduca a los 300 s; si el escaneo activo dura más, el resto de la API se recorre sin autenticar y aprueba por no encontrar nada. Hay un paso que lo detecta y falla; falta un cliente de Keycloak dedicado con `accessTokenLifespan` mayor (issue #46).

---

## 6. Performance Testing — **Cero**

> *"Stress testing, Load testing, Concurrent users, Tiempo de respuesta y Throughput"* · JMeter y/o k6.

**No hay ni una sola prueba de carga.** Es la única de las ocho capas literalmente a cero (**T-3**).

La instrumentación para medirla **sí existe**: `application.yml` tiene los buckets SLO (`50ms, 100ms, 200ms, 500ms, 1s, 2s`) y el histograma de percentiles activo, y `StockServiceConcurrencyIT` cubre corrección bajo concurrencia — pero corrección no es rendimiento. El plan define k6 con `p(95) < 500ms`, stress, load y usuarios concurrentes. Hasta que exista, RNF-08 y RNF-10 quedan implementados pero **sin evidencia de comportamiento bajo carga**.

---

## 7. Data Testing — **Parcial**

> *"Migraciones, Integridad de datos, Datos duplicados, Constraints y Seeds"*

| Aspecto | Estado |
|---|---|
| Migraciones | **Cumple** — 7 migraciones Flyway (`V1`…`V7`); `ProductRepositoryIT` y `AuditIntegrationIT` levantan el esquema real |
| Seeds | **Cumple** — `V5__seed_data.sql` |
| Integridad | Parcial — FK producto↔movimiento validada en `StockServiceConcurrencyIT` |
| Constraints | Parcial — SKU único definido en el esquema, sin test dedicado que verifique el rechazo del duplicado |
| **Datos duplicados** | **Falta** — el enunciado lo nombra explícito; no hay caso que inserte un duplicado y compruebe el rechazo |

Pendiente: **DATA-1/2** — tests de constraints, seeds y datos duplicados.

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
| E2E | `cd e2e && npx playwright test` | `e2e.yml` (C-1/TEST-7), contra el stack desplegado |
| API + Security contra el desplegado | — | `staging.yml` tras el deploy |
| Análisis estático | — | SonarCloud en cada run |

> Los IT **no arrancan sobre Docker Desktop en Windows** (C-4). En local, en Windows, `./mvnw test` (solo unit) sí corre; para los IT hace falta un entorno Linux o el propio CI.

**Reportes generados:** surefire (unit), failsafe (IT), JaCoCo (cobertura), cobertura de frontend e informe de ZAP, todos como artefactos de CI. Faltan los de k6 y Newman, que dependen de T-3 y TEST-3.

---

## 10. Defectos encontrados

17 bugs registrados como issues, con reproducción en el cuerpo. **10 corregidos, 7 abiertos.** Los corregidos se registraron cerrados, cada uno enlazando el PR que lo arregló y declarando que la issue se abrió después del arreglo, para dejar el rastro (T-6, [informe](reportes/T-6-issues-de-bug.md)).

### Abiertos

| # | Defecto | Severidad | Tarea |
|---|---|---|---|
| [#43](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/43) | Keycloak emite cualquier scope a cualquier usuario autenticado | **Crítico** | G-8 (mitigado en backend) |
| [#48](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/48) | `user:manage` no protege ningún endpoint | Alto | A-2 |
| [#44](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/44) | `AuthContext.tsx` replica el bug de primer-rol-gana | Medio | G-3a |
| [#46](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/46) | El token de ZAP caduca a los 300 s y el resto del escaneo corre sin autenticar | Medio | TEST-10b |
| [#47](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/47) | `staging.yml` inyecta `JWT_SECRET` y `JWT_EXPIRATION_MS` que no lee nadie | Medio | S-4b |
| [#45](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/45) | `keycloak-init` no es idempotente | Bajo | P-2b |
| [#49](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/issues/49) | Testcontainers no arranca sobre Docker Desktop en Windows | Bajo (entorno) | C-4 |

El #43 está en abierto a propósito: la escalada existe en el IdP y la corrección de raíz (G-8) sigue pendiente, aunque el backend la mitigue descartando los scopes no permitidos. Cerrarlo antes de G-8 falsearía el estado.

### Corregidos (10)

Escalada por primer-rol-gana (#50) y fallback de scopes (#51), el check de CI que aprobaba en 12 s (#52), el badge de cobertura placeholder (#53), la cobertura de frontend que informaba 100 % midiendo 14 sentencias (#54), Spotless desactivado en 8 puntos (#55), el README que documentaba una API inexistente (#56) y el CORS de `staging` que bloqueaba la demo (#57). Los dos últimos salieron de montar los E2E en CI: el SPA no pedía scopes en el login (#69) y `check-sso` los perdía al refrescar (#70). Cada uno con su PR enlazado.

---

## Qué falta para cerrar la pirámide

Ordenado por lo que el enunciado exige con más fuerza. Los dos obligatorios que faltaban —**TEST-1** (Testcontainers con Keycloak) y **C-1/TEST-7** (E2E en CI)— ya están cerrados y verificados.

1. **T-3** — Performance. Única capa a cero.
2. **T-5, DATA-1/2, TEST-11** — cierres puntuales de Security y Data.
3. **TEST-8, TEST-9, D-4** — snapshots, responsive y accesibilidad, mejoras dentro de E2E.

Trazabilidad completa de cada identificador en el [plan de ejecución](../PLAN_EJECUCION.md), §4.3.
