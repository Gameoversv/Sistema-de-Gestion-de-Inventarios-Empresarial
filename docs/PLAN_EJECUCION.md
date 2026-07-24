# Plan de Ejecución Priorizado

**Fuente de verdad:** `Proyecto_Final_V3.pdf` (revisado íntegro el 2026-07-22)
**Base de hallazgos:** [ANALISIS_BRECHAS.md](ANALISIS_BRECHAS.md)
**Actualizado:** 2026-07-24, sobre `main` en `4243945`, tras cerrar la Ola 2, la Ola 4 salvo C-4 y CI-2, **la Ola 5 completa** (requisitos, arquitectura, mantenimiento, guía de pruebas, T-6 y D-1…D-4), P-2 de la Ola 6, P-2a de la Ola 7, los dos obligatorios de sesión (SEC-2, S-2), los 16 code smells (Q-5) y el alcance funcional pendiente (F-2, D-1, D-2), que se mergeó en el PR #64. La documentación de la Ola 5 está en rama, sin mergear aún.

> **Aviso de método.** La versión anterior de este plan tomaba como requisito el desglose del análisis de brechas, que en algunos puntos era interpretación propia y no texto del enunciado. Cada requisito de este documento está contrastado con el PDF. Cuando algo es criterio nuestro y no del enunciado, se marca como **[criterio propio]**.

---

## 1. Evaluación — las ocho áreas

El enunciado define **ocho** áreas. La versión anterior omitía la última.

| Área | Peso | Inicial | Actual | Alcanzable |
|---|---|---|---|---|
| Funcionalidad | 15% | ~85% | ~97% | ~98% |
| Testing | 20% | ~60% | ~87% | ~90% |
| Seguridad | 10% | ~70% | ~90% | ~90% |
| Observabilidad | 15% | ~30% | ~90% | ~90% |
| CI/CD | 15% | ~60% | ~85% | ~90% |
| Calidad de código | 10% | ~35% | ~85% | ~85% |
| Documentación | 10% | ~25% | ~90% | ~90% |
| **Presentación final** | **5%** | **0%** | **~30%** | **~90%** |

Salvo la cobertura, medida sobre el artefacto de CI, los porcentajes son estimaciones.

| Cobertura (artefacto JaCoCo en Actions) | Inicial | Actual | Umbral |
|---|---|---|---|
| BRANCH | 71,6 % | **85,0 %** | 80 % |
| LINE | 84,9 % | **92,7 %** | 80 % |

Cifras vigentes tras `KeycloakAuthIT` (TEST-1), que ejercita en integración `SecurityConfig` y los controladores con un token real y subió la cobertura desde 84,5/92,1. La medición original de la que parte esta tabla es el artefacto de `798e6b6`. El frontend se mide aparte y está en **9,3 %** de líneas: el test de scopes de G-3a lo subió desde 7,1 %. El informe daba 100 % hasta que se configuró `coverage.include` en vitest, que solo cubría las 14 sentencias que los tests importaban; con esa opción el número es el real.

---

## 2. Entregables exigidos

El enunciado los lista de forma explícita. Sirve como checklist de cierre.

| Entregable | Estado |
|---|---|
| Código fuente completo | listo |
| Docker Compose funcional | listo — **14 servicios** (Redis retirado en INF-1, no lo usaba nadie) |
| Jenkins pipeline | parcial — **11 etapas escritas** y Jenkins configurado como código, pero de `Integration Tests` en adelante nunca se ha ejecutado: hace falta un agente Linux (C-4) |
| GitHub Actions pipeline | **listo** — security scan (ZAP autenticado) y quality gate (SonarCloud) añadidos en la Ola 4 |
| Dashboards Grafana | listo — **4 de 4**; datasources de Prometheus, Tempo y Loki provisionados |
| Reportes de pruebas | parcial — surefire, failsafe, JaCoCo, cobertura de frontend e informe de ZAP como artefactos; faltan k6 y Newman |
| Evidencias QA | **cumple** — 12 informes en `docs/testing/reportes/`, 6 capturas en `docs/testing/capturas/` y 18 issues de bug y charter con reproducción |
| Documentación completa | **cumple** — requisitos, arquitectura, manual de mantenimiento y guía de pruebas entregados |
| **Presentación final funcional** | **en curso** — P-2 hecho; faltan guion (P-1) y ensayo (P-3) |

---

## 3. Lo que cambió respecto al plan original

Siete hallazgos de la ejecución alteran prioridades.

### 3.1 El mapa rol→scopes de Java es el único control de seguridad

Verificado en vivo ([informe](testing/reportes/G-6-escalada-de-scopes.md)): Keycloak emite **cualquier scope a cualquier usuario autenticado**. `inv_viewer` obtuvo `product:manage`, `stock:manage`, `user:manage` y `audit:view`.

**Consecuencia:** G-2 ("mover rol→permisos a Keycloak y eliminar la duplicación") no puede ejecutarse en el orden previsto. Primero hay que restringir los scopes en el IdP (**G-8**), y solo después simplificar el backend.

### 3.2 La cobertura ya pasa el umbral

84,5 % de ramas y 92,1 % de líneas tras cubrir `UnifiedAuditService` y el refactor de Q-5. Desaparece el trabajo de cobertura previsto en la Ola 4. Desde Q-1, SonarCloud la mide en cada ejecución de CI, así que ya no hay que ir a buscar el artefacto.

### 3.3 Había un check de CI que no ejecutaba nada

`Integration Tests` invocaba goals de failsafe sueltos y terminaba en `No tests to run` en 12 s. Corregido; ahora ambos checks son obligatorios en `main`.

### 3.4 Keycloak 24 no expone métricas de eventos

`KC_METRICS_ENABLED` solo aporta métricas de Quarkus: `agroal`, `base`, `jvm`, `netty`, `process`, `system`, `vendor`, `worker`. Cero series de login.

**Pero el enunciado pide "Fallos de autenticación", no "fallos de login en Keycloak".** Un 401 del backend es un fallo de autenticación y satisface el requisito. Se descarta el SPI de terceros. **[criterio propio]** queda anotado en mejoras que un 401 no detecta fuerza bruta contra el formulario de Keycloak, pese a que el realm tiene `bruteForceProtected: true`.

### 3.5 Testcontainers con Keycloak vuelve a ser obligatorio

El enunciado es literal: *"Integration Testing — Obligatorio utilizar: Testcontainers. **Debe probarse: Base de datos real, Keycloak, Integraciones**"*. La versión anterior de este plan bajó TEST-1 de prioridad por error. Se restituye.

### 3.6 "Policies" aparece nombrado en el enunciado

*"modelo de seguridad completamente granular basado en: Keycloak, OAuth2, JWT, Roles, Permisos, Scopes y **Policies**"*. G-1 (Authorization Services) deja de ser puramente opcional: o se implementa, o el ADR debe argumentar por qué los scopes cubren el modelo.

### 3.7 Los E2E destaparon dos bugs de autenticación, no fragilidad de tests

Al montar Playwright en CI (C-1) los flujos de productos y stock fallaban. No eran selectores frágiles: eran **dos defectos reales** que ningún test unitario veía y que en producción dejarían la app inservible.

1. **El SPA no pedía los scopes en el login** (#69). `keycloak.login()` sin `scope`; los siete permisos son *optional scopes*, así que el token no los traía y `PermissionGuard` ocultaba toda la interfaz protegida. Desde el arranque.
2. **Refrescar la página perdía los scopes** (#70). `check-sso` obtiene un token silencioso que no re-pide los optional scopes; tras un F5 la interfaz protegida volvía a desaparecer. Por eso productos pasaba (navega por sidebar, token en memoria) y stock fallaba (`page.goto`, recarga).

Ambos corregidos en el PR de C-1. **Consecuencia de método:** los E2E no son verificación cosmética; son la única capa que ejercita el token real en el browser, y aquí pagaron su coste de sobra.

---

## 4. Trazabilidad: requisito del PDF → estado

### 4.1 Alcance funcional (15%)

| Requisito | Estado |
|---|---|
| Productos: agregar, editar, eliminar, visualizar con paginación/búsqueda/filtros/ordenamiento | **cumple** — F-2: ordenamiento por SKU, nombre, precio y stock, con `aria-sort` |
| Stock: actualizar, alertas por mínimo, historial con 6 campos | cumple |
| Auditoría con Hibernate Envers | cumple |
| API REST con OpenAPI y Swagger UI | cumple |
| Dashboard: productos críticos, **más vendidos**, historial reciente, métricas, indicadores | **cumple** — D-1 (`/api/reports/best-sellers`, agregando movimientos `OUT`) y D-2 (lista de críticos, no solo el contador) |

### 4.2 Seguridad (10%)

| Requisito | Estado |
|---|---|
| Keycloak + OAuth2 + JWT | cumple |
| Matriz de 7 permisos | cumple — `user:manage` existe pero no protege nada (**A-2**) |
| Protección de endpoints por permiso, no por rol | cumple — `@PreAuthorize` con `SCOPE_` |
| Refresh tokens | **cumple** — S-2: 5 comprobaciones en el paso de API tests de `staging.yml`, contra Keycloak vivo |
| Expiración de sesiones | **cumple** — SEC-2: `onTokenExpired` y `onAuthRefreshError` conectados en `src/lib/session.ts`, con 5 tests |
| Policies | **G-1** — implementar o justificar por ADR |

### 4.3 Full Stack Testing (20%) — las 8 capas

| Capa | Exigencia | Estado |
|---|---|---|
| 1. Unit | Servicios, validaciones, lógica | cumple — **307 `@Test`** en 33 ficheros. La cifra de 284 que traía este plan es la del pipeline de Jenkins; SEC-2, Q-5 y F-2/D-1/D-2 añadieron tests después |
| 2. Integration | Testcontainers: **BD real, Keycloak**, integraciones | **cumple** — BD (y desde ENV-1 contra la base desplegada) y **Keycloak real con `KeycloakAuthIT`** (TEST-1), verificado en CI |
| 3. API / Contract | Endpoints, contratos OpenAPI, status codes, payloads | parcial — Postman sin CI (TEST-3), RestAssured sin uso (TEST-2) |
| 4. E2E | Snapshots, flujos, navegación, **roles**, seguridad, **responsive** | **cumple** — `e2e.yml` (C-1/TEST-7) corre los 3 specs (12 casos) contra el stack desplegado, **12/12 en verde**; faltan snapshots (TEST-8) y responsive (TEST-9) como mejora |
| 5. Security | ZAP, JWT, permisos, CORS, Dependency Check/Snyk, autenticación | parcial — **ZAP autenticado** sembrado con el OpenAPI y con umbral (TEST-10); faltan T-5 y TEST-11 |
| 6. Performance | Stress, load, usuarios concurrentes, tiempo de respuesta, throughput | **cero** (T-3) |
| 7. Data | Migraciones, integridad, **duplicados**, constraints, seeds | parcial (DATA-1/2, E-1) |
| 8. Exploratory | Charters, bugs encontrados, escenarios | **cumple** — 3 charters y 15 bugs como issues, más los informes en `docs/testing/reportes/` (T-6) |

### 4.4 Observabilidad (15%)

| Componente | Estado |
|---|---|
| Metrics — Prometheus | **cumple** — 5 targets tras OBS-3 |
| Traces — Tempo | **cumple** — trazas consultables, servicio `inventory-api` |
| Logs — Loki | **cumple** — ingiere los 14 contenedores, consultable por `{service=...}` |
| Collector — Alloy | **cumple** — recibe OTLP y reenvía a Tempo; recoge logs y los envía a Loki |
| Dashboards — Grafana | **cumple** — 4 de 4, verificados panel por panel |
| Alerting — Alertmanager | **cumple** — las 5 obligatorias (dos verificadas disparando) + 1 de negocio |
| Instrumentación — OpenTelemetry | **cumple** — bridge OTel + exportador OTLP |

**Los 7 componentes obligatorios implementados y los 4 dashboards separados.** La Ola 2 queda cerrada; lo que resta del área es capturar evidencia con datos reales para la presentación.

| Señal | Exigencia del PDF | Estado |
|---|---|---|
| **Métricas** | CPU, Memoria, JVM, Latencia, Throughput, Error rate, DB pool | **7 de 7** — CPU y memoria de host desde OBS-3 |
| **Logs** | traceId, spanId, correlationId, nivel, usuario, endpoint | **6 de 6** — [informe](testing/reportes/OBS-4-logs-loki.md); solo en perfil `staging`/`prod` |
| **Trazas** | request, database, external calls, errores distribuidos | **3 de 4** — falta verificar errores distribuidos |
| **Alertas** | CPU, error rate, latencia, servicios caídos, fallos de autenticación | **5 de 5** — dos verificadas disparando; +1 de negocio (`ProductosBajoMinimo`) |
| **Métricas de negocio** | [criterio propio] movimientos, unidades, alertas y productos bajo mínimo | **4 series** — [informe](testing/reportes/OBS-2-E-3-metricas-de-negocio.md) |

### 4.5 CI/CD (15%)

El enunciado exige **10 etapas** de pipeline: Checkout, Build, Unit tests, Integration tests, API tests, E2E tests, Security scan, Quality gates, Docker build, Deployment.

| Etapa | GitHub Actions | Jenkins |
|---|---|---|
| Checkout | sí | sí — ejecutado |
| Build | sí | sí — ejecutado |
| Unit tests | sí | sí — ejecutado, 284 en verde |
| Integration tests | sí | escrita, **sin ejecutar** |
| API tests | staging.yml | escrita (smoke), sin ejecutar |
| E2E tests | `e2e.yml` (C-1/TEST-7) — **12/12 verde** | escrita, sin ejecutar |
| Security scan | staging.yml — ZAP autenticado sobre el OpenAPI | escrita, sin ejecutar |
| Quality gates | JaCoCo + SonarCloud | escrita, sin ejecutar |
| Docker build | sí | escrita, sin ejecutar |
| Deployment | staging.yml | escrita, sin ejecutar |

**GitHub Actions cubre las 10 etapas.** La última que faltaba, E2E, corre en `e2e.yml` (C-1/TEST-7) con los 12 casos en verde contra el stack desplegado.

Jenkins pasa de 8 a **11 etapas** y de una instalación vacía a configuración como código en `docker/jenkins/`. Pero solo las cuatro primeras se han llegado a ejecutar: la de integración no arranca sobre Docker Desktop en Windows y bloquea todo lo que va detrás (**C-4**, ver el aviso de Testcontainers más abajo).

### 4.6 Calidad de código (10%)

Exige SonarQube o SonarCloud midiendo Coverage, Bugs, Vulnerabilities, Code smells y Duplicación. **Cubierto en la Ola 4** (Q-1): SonarCloud analiza en cada ejecución de CI y el README publica las cinco métricas más el quality gate.

Primer análisis de `main`: cobertura 88,1 %, 0 bugs, 0 vulnerabilidades, 0 % de duplicación y **16 code smells**, deuda preexistente que nadie había medido porque Sonar nunca se había ejecutado. **Los 16 resueltos** (Q-5): 3 en código de producción y 13 en tests. Dos dejaron de ser cosméticos al mirarlos —una lista de `Future` que se llenaba y nunca se leía, y un bloque `catch` vacío sin justificar—, y uno era falso positivo por idioma. [Informe](testing/reportes/Q-5-code-smells.md)

Spotless estaba declarado en el POM y desactivado con `-Dspotless.check.skip=true` en los ocho sitios donde se invoca Maven. Retirado el flag (Q-2): al correr en fase `validate`, ahora se comprueba en cada `compile`, `test`, `verify` y `package`.

### 4.7 Repositorio y buenas prácticas

| Requisito | Estado |
|---|---|
| Repositorio público | cumple |
| README profesional | cumple — rutas reales, matriz rol→scopes y el `scope` obligatorio del token |
| **Issues** | **33 issues**: 13 épicas de fase, **17 bugs** (7 abiertos) y **3 charters**. Los dos últimos bugs (#69, #70) salieron de montar los E2E en CI. Las épicas 9, 10 y 11 se pusieron al día en la Ola 4 |
| Pull Requests | cumple — **33 PRs**, el último el #64 |
| Branch strategy | cumple — `main` protegida; corren 4 checks y 2 son obligatorios |
| Conventional Commits | cumple — commitlint activo |
| Code Reviews | **riesgo** — BP-1 contaba 6 de 10 PRs sin revisión, y los mergeados el 23-07 tampoco la tuvieron. Es evaluable y `eduardolp-pz` tiene permiso de escritura, así que su aprobación cuenta |
| Branch protection | cumple |
| Secrets management / sin credenciales hardcodeadas | cumple tras BP-2 |
| **Participación equitativa de ambos integrantes** | **riesgo** — evaluable según commits, ramas, issues y PRs |

---

## 5. Trabajo pendiente, priorizado

### Ola 2 — Observabilidad (≈13 h) · **COMPLETA**

Era el mayor déficit del proyecto: cinco de los siete componentes obligatorios estaban ausentes. Las ocho tareas están cerradas y verificadas en vivo, cada una con su informe.

| # | Acción | Esfuerzo | Estado |
|---|---|---|---|
| OBS-3 | node-exporter, postgres-exporter, `KC_METRICS_ENABLED` | 1,5 h | **hecho** — 5/5 targets up |
| OBS-5 | `rules/alerts.yml` con las 5 alertas + Alertmanager | 2 h | **hecho** — verificadas disparando |
| OBS-1 | Bridge OTel + exportador OTLP + **instrumentación JDBC** | 2 h | **hecho** — 12 spans por petición |
| — | Tempo, Alloy y **Loki** en compose | 2,5 h | **hecho** — Loki ingiere los 14 contenedores |
| **OBS-4** | Filtro MDC: `correlationId`, usuario, endpoint + logback con `traceId`/`spanId` | 1,5 h | **hecho** — 6/6 campos, [informe](testing/reportes/OBS-4-logs-loki.md) |
| OBS-6 | Datasources `tempo.yml` y `loki.yml` con correlación traces↔logs | 30 min | **hecho** — derived field logs→trazas y `tracesToLogsV2` trazas→logs |
| **OBS-2 + E-3** | `Counter` de alertas de stock y de movimientos por tipo | 1,5 h | **hecho** — 4 series + alerta de negocio, [informe](testing/reportes/OBS-2-E-3-metricas-de-negocio.md) |
| **—** | Separar en 4 dashboards: Infraestructura, Aplicación, Negocio, Seguridad | 3 h | **hecho** — 4 de 4, 37 consultas sin paneles vacíos, [informe](testing/reportes/OBS-dashboards.md) |

> **Decisión tomada:** el stack de la demo se levanta con `SPRING_PROFILES_ACTIVE=demo`. La demo necesita a la vez JSON estructurado —sin él el panel de logs no puede filtrar por usuario ni por endpoint— y un frontend en `localhost:3000`, y ningún perfil existente daba las dos cosas: `staging` y `prod` emiten JSON pero apuntan el CORS a dominios que no existen en local, y `dev` abre el CORS pero emite texto plano.
>
> **Resuelto en P-2a.** Se descartó añadir `localhost` a `staging`, que declara espejar producción y sí se usa en el workflow de despliegue. El perfil `demo` es copia de `staging` con CORS local y muestreo de trazas al 100 %, y `CorsProfilesTest` impide que alguien vuelva a "arreglarlo" metiendo localhost en `staging` o `prod` ([informe](testing/reportes/P-2a-perfil-demo.md)). P-3 deja de estar bloqueado.

El área queda cerrada: el pendiente que arrastraba (P-2) está hecho.

### Ola 3 — Capas de testing ausentes (≈14 h)

| # | Acción | Esfuerzo | Capa |
|---|---|---|---|
| ~~**C-1 + TEST-7**~~ | ~~Playwright en CI~~ — **hecho y verificado, 12/12 en verde**. `e2e.yml` despliega el stack con perfil demo (`docker compose up --build`), espera a Keycloak/backend/frontend y corre los 3 specs contra el sistema vivo, subiendo el informe como artefacto. Destapó **dos bugs de auth reales** (#69, #70), no fragilidad de los specs: el SPA no pedía scopes en el login, y `check-sso` los perdía al refrescar. Ambos corregidos en el mismo PR. No corre en local por C-4 | 3 h | 4 |
| **TEST-9** | Responsive: 375 / 768 / 1440 px | 45 min | 4 |
| **TEST-8** | `toHaveScreenshot()` en dashboard, productos y stock | 1 h | 4 |
| ~~**TEST-1**~~ | ~~`dasniko/testcontainers-keycloak` + IT con token real — **obligatorio**~~ — **hecho y verificado en CI**. `KeycloakAuthIT` levanta Keycloak y Postgres reales, obtiene un token por password grant y ejercita la cadena entera (JWKS, firma, emisor, intersección de scopes, `@PreAuthorize`); 4 tests en verde en el job `integration-test`, incluida la reverificación de G-8 a nivel IT. Afinado en 5 iteraciones sobre CI (imagen del contenedor, required actions, User Profile, `realm_access`), ya que C-4 impide correrlo en local | 3 h | 2 |
| **T-3** | k6: load, stress, usuarios concurrentes, `p(95)<500ms` | 3 h | 6 |
| **T-5** | OWASP Dependency Check y `npm audit`/Snyk en CI | 45 min | 5 |
| **TEST-3 + TEST-2** | Newman en CI + RestAssured para contrato OpenAPI | 2 h | 3 |
| **TEST-11** | Test de CORS por perfil | 30 min | 5 |
| **DATA-1/2** | Constraints, seeds y **datos duplicados** | 1,5 h | 7 |

> **C-1 + TEST-7 es la mejor palanca disponible.** El enunciado exige E2E con roles, seguridad y responsive, ejecutados *contra el sistema desplegado*. Además es lo único que confirmaría SEC-1.

### Ola 4 — Calidad y CI/CD (≈7 h)

| # | Acción | Esfuerzo | Estado |
|---|---|---|---|
| **Q-2** | Retirar `-Dspotless.check.skip=true` de los 8 puntos | 45 min | **hecho** — spotless corre en `validate`, así que ahora se comprueba en cada `compile`, `test`, `verify` y `package` |
| **Q-3** | Job de frontend en CI: lint + coverage | 45 min | **hecho** — el frontend no tenía ningún job; ahora corre lint y tests con cobertura real |
| **Q-4** | Publicar cobertura como artefacto + badge | 30 min | **hecho** — artefacto `coverage-report`, resumen en cada run y badges verificados en CI |
| **Q-1** | SonarCloud con las 5 métricas exigidas + badge | 1,5 h | **hecho** — análisis en cada run de CI; 6 badges (quality gate + las 5 métricas) servidos por SonarCloud |
| **C-4** | Jenkins: añadir E2E, security scan y quality gate | 3 h | **parcial** — Jenkins pasa a configurarse como código (`docker/jenkins/`) y el pipeline se ejecuta por primera vez. Corregida la etapa `Unit Tests`, que arrastraba los IT: ahora 284 tests en verde. Añadidas Quality Gate, E2E y Security Scan, validadas de sintaxis con el linter declarativo. **Sin validar en ejecución**: `Integration Tests` no corre bajo Docker Desktop en Windows (ver abajo), y con ella quedan sin alcanzar las tres nuevas |
| **CI-2** | Tag `v1.0.0` y primera ejecución de `production.yml` | 15 min | pendiente — crea un GitHub Release, decisión explícita |
| **—** | Smoke test post-release | 45 min | pendiente — depende de CI-2 |
| **TEST-10** | ZAP autenticado o `zap-full-scan` con umbral | 2 h | **hecho** — `zap-api-scan` sembrado con el OpenAPI y autenticado; sin `-I`, así que un WARN nuevo tumba el despliegue. Validado en local: 29 URLs, 118 reglas PASS, 0 WARN |
| **ENV-1** | IT con URL de BD por configuración externa | 1 h | **hecho** — `LiveDatabaseIT` + perfil `live-db-it`; verificado que falla cuando no hay base, en vez de saltarse |

> **El badge de cobertura del README era falso.** Decía `coverage-placeholder-brightgreen`: verde fijo, sin medir nada. Ahora hay tres badges con los valores reales y [`scripts/verificar-badges-cobertura.sh`](../scripts/verificar-badges-cobertura.sh) falla en CI si se desfasan. No se generan SVG desde el runner a propósito: `main` exige PR con revisión, así que un push automático quedaría bloqueado por la propia protección de rama.

> **Testcontainers no arranca sobre Docker Desktop en Windows.** `./mvnw verify` local termina en `Could not find a valid Docker environment` en los 3 IT, con Docker Desktop levantado y también forzando `DOCKER_HOST=npipe:////./pipe/docker_engine`.
>
> Confirmado también **dentro del contenedor de Jenkins**, donde el socket sí está montado y el cliente de Docker funciona: `/info` responde `400` con `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`. Probado con `DOCKER_HOST` y `TESTCONTAINERS_DOCKER_CLIENT_STRATEGY` explícitos (build #4) y sin ellos (build #3), con idéntico resultado. Es el proxy del socket de Docker Desktop, no la configuración.
>
> En los runners Linux de GitHub Actions esos mismos IT pasan en cada PR. **Consecuencia práctica:** la etapa `Integration Tests` de Jenkins —y por tanto todo lo que va detrás— solo se puede validar en un agente Linux.

### Ola 5 — Documentación · **COMPLETA**

| Documento | Exigencia del PDF | Esfuerzo | Estado |
|---|---|---|---|
| **`docs/requisitos/`** | "documento detallado de requisitos funcionales y no funcionales" | 3 h | **hecho** — **22 RF y 24 RNF**, cada uno con su cita del PDF, el `fichero:línea` que lo implementa y la prueba que lo verifica. 35 cumplen, 9 parciales, **2 pendientes**: Policies (RNF-05) y tiempo de respuesta bajo carga (RNF-08) |
| **`docs/arquitectura/`** | "diagramas de arquitectura, guías de instalación y manuales de mantenimiento" | 3 h | **hecho** — vista de componentes con 5 diagramas Mermaid (contexto, componentes, arranque, 3 flujos), estructura de backend y frontend, y guía de instalación con verificación de la cadena de auth. Destapó **INF-1**: Redis desplegado, configurado y sin un solo uso en el código |
| **`docs/operacion/manual-mantenimiento.md`** | idem + **la trampa del volumen de Keycloak** | 2 h | **hecho** — qué vigilar, rutinas, respaldo/recuperación y la trampa del volumen de Keycloak con su tabla objetivo→acción. Enlaza P-2b y S-4b |
| **`docs/testing/guia-de-pruebas.md`** | "casos de prueba, resultados y cualquier defecto encontrado" | 2 h | **hecho** — las 8 capas de testing una a una: qué se prueba, con qué, dónde vive, resultado y qué falta. 307 tests desglosados por área; los 15 bugs con severidad y tarea, 7 abiertos. Cierra la trazabilidad hacia `reportes/` |
| **T-6** | Charters y bugs como **issues de GitHub** | 1 h | **hecho** — 15 bugs (#43…#57) y 3 charters (#58…#60). Los 8 ya corregidos se registraron cerrados, cada uno enlazando su PR y declarando en el cuerpo que se abrieron después del arreglo. [Informe](testing/reportes/T-6-issues-de-bug.md) |
| **D-1…D-4** | README: stack real, badge, rutas correctas | 45 min | **hecho** — declaraba `Base URL: /api/v1`, que no existe, y un `client_id` y unos usuarios que tampoco. Corregido con las rutas reales, la matriz rol→scopes y el aviso del `scope` obligatorio, sin el cual toda petición responde 403. `CONTRIBUTING` alineado con los prefijos de rama que se usan de verdad |

> **T-6 dejó dos hallazgos nuevos.** Redactar las issues obligó a releer el código, no solo los informes. Salió que [`AuthContext.tsx`](../frontend/src/contexts/AuthContext.tsx) arrastra **los dos** defectos de scopes corregidos en el backend por el PR #33 —G-4 y también G-5, que el plan no había anotado— y que `JWT_SECRET` no está en `application-staging.yml` sino en el workflow `.github/workflows/staging.yml`, con un secreto de repositorio vivo que no protege nada.

### Ola 6 — Presentación final (5%, ≈2 h restantes) · EN CURSO

Es un entregable explícito: *"presentación final funcional del sistema en clase"*.

| # | Acción | Esfuerzo | Estado |
|---|---|---|---|
| **P-1** | Guion de demo: alta de producto → movimiento de stock → alerta → auditoría → dashboard | 1 h | pendiente |
| **P-2** | Capturas de los 4 dashboards con datos reales y de una alerta disparada | 1 h | **hecho** — 6 capturas, 34 paneles sin ninguno vacío, alerta verificada hasta Alertmanager, [informe](testing/reportes/P-2-capturas-de-evidencia.md) |
| **P-3** | Ensayo con el stack levantado desde cero (`down -v && up`) | 1 h | pendiente — el CORS ya no bloquea, resuelto en P-2a. **Queda un bloqueo real: P-2b**, porque `keycloak-init` no es idempotente y el ensayo es precisamente un `up` repetido. Hacer P-2b antes |

> **Dos avisos de P-2 que afectan al guion de P-1.** Los paneles de Negocio usan `increase()`: si en la demo se encadenan todos los movimientos seguidos saldrán en cero, porque Prometheus no puede medir el incremento del primer punto de una serie. Hay que espaciarlos o levantar el stack con antelación. Y la ventana temporal de los dashboards no debe abarcar un reinicio del backend con otro perfil, o cada panel duplica sus series.

### Ola 7 — Deuda abierta por los hallazgos · **COMPLETA**

| # | Acción | Esfuerzo |
|---|---|---|
| ~~**G-3a**~~ | ~~Unión de scopes en `AuthContext.tsx`; hoy replica el bug de primer-rol-gana~~ — **hecho**: extraído a `lib/scopes.ts` (función pura, sin el init de Keycloak de por medio) y reescrito como unión espejando el backend. Rol desconocido no aporta nada; sin rol, deniega. 6 tests nuevos, incluido el multi-rol y la independencia del orden. Cierra #44 | — |
| ~~**INF-1**~~ | ~~Redis desplegado, configurado y sin un solo uso en el código~~ — **hecho**: retirado del `docker-compose.yml` (14 servicios), del POM, de `application.yml`/`-dev`/`-smoke`, de `.env.example` y `staging.yml`. Quitadas también las exclusiones de autoconfig de Redis en los 4 IT, que ya no tienen sentido. Backend compila y los 289 unit tests siguen en verde. Aplicó la regla 3 | — |
| ~~**G-8**~~ | ~~`scope-mappings` por rol en Keycloak: corrección de raíz de G-6~~ — **hecho y verificado en CI**. `init-users.sh` ata cada client scope a los roles autorizados. El paso "G-8" de `staging.yml` (run [30070253945](https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/actions/runs/30070253945), verde) probó a nivel de token que un `inv_viewer` pidiendo scopes elevados recibe solo `product:view`. La sospecha de que los scope-mappings no gatearían el string resultó falsa: sí lo gatean. El mapa Java (ADR-002) se conserva como defensa en profundidad. Cierra #43 | — |
| ~~**S-4b**~~ | ~~Quitar `JWT_SECRET` y `JWT_EXPIRATION_MS` de `staging.yml`~~ — **hecho**: ningún Java los leía (la firma la valida Keycloak). Retirados del workflow y de `GITHUB_SECRETS.md`. Queda borrar el secreto `STAGING_JWT_SECRET` en la config del repo, a mano. Cierra #47 | — |
| ~~**ADR-002**~~ | ~~Por qué el mapa rol→scopes vive en Java~~ — **hecho**: [`ADR-002`](decisions/ADR-002-mapa-rol-scopes-en-java.md) documenta la contención de G-6 en el backend, con `SecurityConfig` apuntando a él | — |
| ~~**P-2a**~~ | ~~CORS de `staging` bloquea el frontend local y deja P-3 sin interfaz~~ — **hecho**: perfil `demo` propio, con CORS local y muestreo al 100 %. Se descartó añadir localhost a `staging`, que espeja producción. Verificado en vivo y fijado con `CorsProfilesTest` [informe](testing/reportes/P-2a-perfil-demo.md) | — |
| ~~**P-2b**~~ | ~~`keycloak-init` no es idempotente: al reejecutarse sobre un realm existente lanza `duplicate key … uk_cli_scope`~~ — **hecho**: comprueba existencia antes de crear scopes y usuarios. Verificado con `sh -n` y lógica; sin doble `down -v && up` en vivo por C-4. Cierra #45 | — |
| **TEST-10b** | El realm emite tokens de 300 s y el escaneo activo de ZAP puede durar más. Al caducar, el resto de la API se recorre sin autenticar y el escaneo aprueba precisamente por no encontrar nada. Ya hay un paso que lo detecta y falla; falta la corrección de raíz: un cliente de Keycloak dedicado al escaneo con `accessTokenLifespan` mayor | 45 min |

### Ola 8 — Alto coste, decidir explícitamente

| # | Acción | Esfuerzo | Alternativa |
|---|---|---|---|
| **G-1** | Authorization Services: Resources, Policies, Permissions — **"Policies" está nombrado en el enunciado** | 5 h | ADR argumentando que scopes + roles cubren el modelo |
| **G-2** | Mover rol→permisos a Keycloak (depende de G-8) | 4 h | ADR-002 |
| **A-2 / M-1** | `UserController` sobre la Admin API — daría uso a `user:manage` | 4 h | ADR delegando a la consola |
| **A-1** | Unificar rutas bajo `/api/v1` | 3 h | **Riesgo alto** cerca de la entrega. El mínimo ya está hecho: el README documenta las rutas reales y declara la inconsistencia en vez de disimularla |

### Mejoras funcionales (≈3,5 h restantes)

| # | Acción | Esfuerzo |
|---|---|---|
| ~~**D-1**~~ | ~~"Productos más vendidos"~~ — **hecho**: endpoint nuevo con agregación en BD. El panel anterior mostraba precio × stock, que mide lo guardado y no lo vendido. [Informe](testing/reportes/F-2-D-1-D-2-alcance-funcional.md) | — |
| ~~**D-2**~~ | ~~Listar productos críticos~~ — **hecho**: el dashboard mostraba solo el contador. De paso, el tipo TS de `CriticalStockResponse` estaba mal y rompía las `key` de React | — |
| ~~**F-2**~~ | ~~Ordenamiento en la tabla de productos~~ — **hecho**: el backend ya lo aceptaba, el hook lo fijaba a `name`. Destapó que un `sort` inválido daba **500**, ahora 400 | — |
| D-3 | `topProducts` con query en BD | 45 min |
| M-2 | `GET /api/stock/{productId}` con `stock:view` | 30 min |
| F-1 | ADR-003 sobre el soft delete — renumerado desde 002, que queda para el mapa rol→scopes de la Ola 7 | 30 min |
| D-4 | `@axe-core/playwright` en E2E | 1 h |
| E-2 | Validación condicional de `quantity` | 45 min |
| ~~**SEC-2**~~ | ~~`onTokenExpired`~~ — **hecho**: renovación proactiva más cierre de sesión cuando el refresco falla. [Informe](testing/reportes/SEC-2-S-2-ciclo-de-vida-del-token.md) | — |
| ~~**S-2**~~ | ~~Test con `grant_type=refresh_token`~~ — **hecho**: 5 comprobaciones, incluida la negativa (refresh inválido → 400) | — |
| — | ~~**[criterio propio]** Métricas de login de Keycloak vía SPI~~ — **descartada**: Keycloak sí emite `LOGIN_ERROR` con usuario, IP y motivo al log, y Loki lo indexa. Ya visible en el dashboard de Seguridad ([informe](testing/reportes/OBS-dashboards.md)) | — |

---

## 6. Qué hacer con el tiempo que quede

Las cifras de abajo son el hueco que falta por cerrar en cada área, no su peso. Es lo que importa para decidir: Testing pesa el doble que cualquier otra, pero ya está en 81 %, así que rinde menos por hora que Documentación.

| Bloque | Puntos en juego | Horas | Puntos/hora |
|---|---|---|---|
| Ola 7 — deuda de los hallazgos | — | 4,5 h | alta: S-4b es un secreto vivo y P-2b bloquea P-3 |
| Mejoras funcionales restantes (D-3, M-2, F-1, D-4, E-2) | 0,50 | 3,5 h | 0,14 |
| Ola 3 — Testing | 2,40 | 14 h | 0,17 |

**Ya hecho:** T-6, SEC-2, S-2, los 16 code smells, el alcance funcional (F-2, D-1, D-2) y **la Ola 5 completa** (requisitos, arquitectura, mantenimiento y guía de pruebas).
Cerró dos obligatorios del enunciado, el único hueco de Calidad, la ausencia total de issues de tipo bug, los tres huecos de Funcionalidad y **el mayor déficit que quedaba: Documentación, de ~25 % a ~90 %**.

**Con la Ola 5 cerrada, el siguiente mejor rendimiento es la Ola 7** (deuda barata de los hallazgos): S-4b es un secreto vivo de 10 min y P-2b (30 min) desbloquea el ensayo de la presentación.

**12 horas:** Ola 7 + C-1/TEST-7 (E2E en CI, la única etapa que falta en Actions) + TEST-1 (Testcontainers con Keycloak, obligatorio).

> **Reservar las últimas 2 horas para la Ola 6:** el guion (P-1) y el ensayo (P-3). Antes del ensayo hay que cerrar **P-2b** (30 min), o el `down -v && up` repetido revienta en `keycloak-init`.

---

## 7. Reglas de trabajo

1. **Contrastar contra el PDF antes de decidir alcance.** Dos decisiones de esta sesión se tomaron sobre el análisis de brechas en lugar del enunciado, y una de ellas casi cuesta 1,5 h en una integración no exigida.
2. **Medir sobre build limpio.** Una medición de cobertura sobre un `target/` obsoleto dio 85,5 % cuando el valor real era 71,6 %. La cifra que vale es la del artefacto de CI.
3. **Nada de configuración decorativa.** Van tres casos encontrados: el trigger a una rama muerta, el check de CI que no ejecutaba nada y el secreto de un cliente `bearerOnly`. Si algo no se ejecuta, o se conecta o se borra.
4. **Cada corrección con su evidencia**, archivada en `docs/testing/reportes/`.
5. **Reviews reales.** Cada PR con aprobación del otro integrante. Es evaluable y sale gratis.
6. **Participación equitativa.** El enunciado evalúa *"commits individuales, ramas, issues y pull requests"* de ambos. Repartir las olas explícitamente.
7. **Los bugs van a Issues.** La regla nació porque había 13 issues y ninguno era un bug, pese a haberse encontrado y corregido varios documentados. T-6 lo cerró: hoy son **33 issues** — 13 épicas, 17 bugs y 3 charters. Todo defecto nuevo abre su issue, también si se arregla en el acto (así se registraron #69 y #70).
