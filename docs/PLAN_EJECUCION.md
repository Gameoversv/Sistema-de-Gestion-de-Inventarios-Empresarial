# Plan de Ejecución Priorizado

**Fuente de verdad:** `Proyecto_Final_V3.pdf` (revisado íntegro el 2026-07-22)
**Base de hallazgos:** [ANALISIS_BRECHAS.md](ANALISIS_BRECHAS.md)
**Actualizado:** 2026-07-22, tras cerrar la Ola 2 completa (Loki, OBS-4, OBS-6, OBS-2/E-3 y los 4 dashboards).

> **Aviso de método.** La versión anterior de este plan tomaba como requisito el desglose del análisis de brechas, que en algunos puntos era interpretación propia y no texto del enunciado. Cada requisito de este documento está contrastado con el PDF. Cuando algo es criterio nuestro y no del enunciado, se marca como **[criterio propio]**.

---

## 1. Evaluación — las ocho áreas

El enunciado define **ocho** áreas. La versión anterior omitía la última.

| Área | Peso | Inicial | Actual | Alcanzable |
|---|---|---|---|---|
| Funcionalidad | 15% | ~85% | ~85% | ~98% |
| Testing | 20% | ~60% | ~78% | ~90% |
| Seguridad | 10% | ~70% | ~85% | ~90% |
| Observabilidad | 15% | ~30% | ~90% | ~90% |
| CI/CD | 15% | ~60% | ~85% | ~90% |
| Calidad de código | 10% | ~35% | ~60% | ~85% |
| Documentación | 10% | ~25% | ~40% | ~90% |
| **Presentación final** | **5%** | **0%** | **~30%** | **~90%** |

Salvo la cobertura, medida sobre el artefacto de CI, los porcentajes son estimaciones.

| Cobertura (artefacto JaCoCo en Actions) | Inicial | Actual | Umbral |
|---|---|---|---|
| BRANCH | 71,6 % | **84,2 %** | 80 % |
| LINE | 84,9 % | **92,2 %** | 80 % |

Medido sobre el artefacto de CI de `main` (`798e6b6`). El frontend se mide aparte y está en **5,4 %** de líneas: hasta ahora el informe daba 100 %, pero solo cubría las 14 sentencias que los tests importaban. Con `coverage.include` en la config de vitest el número es el real.

---

## 2. Entregables exigidos

El enunciado los lista de forma explícita. Sirve como checklist de cierre.

| Entregable | Estado |
|---|---|
| Código fuente completo | listo |
| Docker Compose funcional | listo — **14 servicios** |
| Jenkins pipeline | parcial — faltan 3 de las 10 etapas |
| GitHub Actions pipeline | parcial — faltan security scan y quality gate |
| Dashboards Grafana | listo — **4 de 4**; datasources de Prometheus, Tempo y Loki provisionados |
| Reportes de pruebas | parcial — surefire, failsafe y ZAP; faltan k6 y Newman |
| Evidencias QA | parcial — 7 informes en `docs/testing/reportes/` y 6 capturas en `docs/testing/capturas/` |
| Documentación completa | parcial |
| **Presentación final funcional** | **en curso** — P-2 hecho; faltan guion (P-1) y ensayo (P-3) |

---

## 3. Lo que cambió respecto al plan original

Seis hallazgos de la ejecución alteran prioridades.

### 3.1 El mapa rol→scopes de Java es el único control de seguridad

Verificado en vivo ([informe](testing/reportes/G-6-escalada-de-scopes.md)): Keycloak emite **cualquier scope a cualquier usuario autenticado**. `inv_viewer` obtuvo `product:manage`, `stock:manage`, `user:manage` y `audit:view`.

**Consecuencia:** G-2 ("mover rol→permisos a Keycloak y eliminar la duplicación") no puede ejecutarse en el orden previsto. Primero hay que restringir los scopes en el IdP (**G-8**), y solo después simplificar el backend.

### 3.2 La cobertura ya pasa el umbral

83,2 % de ramas tras cubrir `UnifiedAuditService`. Desaparece el trabajo de cobertura previsto en la Ola 4.

### 3.3 Había un check de CI que no ejecutaba nada

`Integration Tests` invocaba goals de failsafe sueltos y terminaba en `No tests to run` en 12 s. Corregido; ahora ambos checks son obligatorios en `main`.

### 3.4 Keycloak 24 no expone métricas de eventos

`KC_METRICS_ENABLED` solo aporta métricas de Quarkus: `agroal`, `base`, `jvm`, `netty`, `process`, `system`, `vendor`, `worker`. Cero series de login.

**Pero el enunciado pide "Fallos de autenticación", no "fallos de login en Keycloak".** Un 401 del backend es un fallo de autenticación y satisface el requisito. Se descarta el SPI de terceros. **[criterio propio]** queda anotado en mejoras que un 401 no detecta fuerza bruta contra el formulario de Keycloak, pese a que el realm tiene `bruteForceProtected: true`.

### 3.5 Testcontainers con Keycloak vuelve a ser obligatorio

El enunciado es literal: *"Integration Testing — Obligatorio utilizar: Testcontainers. **Debe probarse: Base de datos real, Keycloak, Integraciones**"*. La versión anterior de este plan bajó TEST-1 de prioridad por error. Se restituye.

### 3.6 "Policies" aparece nombrado en el enunciado

*"modelo de seguridad completamente granular basado en: Keycloak, OAuth2, JWT, Roles, Permisos, Scopes y **Policies**"*. G-1 (Authorization Services) deja de ser puramente opcional: o se implementa, o el ADR debe argumentar por qué los scopes cubren el modelo.

---

## 4. Trazabilidad: requisito del PDF → estado

### 4.1 Alcance funcional (15%)

| Requisito | Estado |
|---|---|
| Productos: agregar, editar, eliminar, visualizar con paginación/búsqueda/filtros/ordenamiento | **F-2** — falta ordenamiento en la tabla |
| Stock: actualizar, alertas por mínimo, historial con 6 campos | cumple |
| Auditoría con Hibernate Envers | cumple |
| API REST con OpenAPI y Swagger UI | cumple |
| Dashboard: productos críticos, **más vendidos**, historial reciente, métricas, indicadores | **D-1, D-2** — faltan 2 de 5 |

### 4.2 Seguridad (10%)

| Requisito | Estado |
|---|---|
| Keycloak + OAuth2 + JWT | cumple |
| Matriz de 7 permisos | cumple — `user:manage` existe pero no protege nada (**A-2**) |
| Protección de endpoints por permiso, no por rol | cumple — `@PreAuthorize` con `SCOPE_` |
| Refresh tokens | **S-2** — sin test |
| Expiración de sesiones | **SEC-2** — sin manejo de `onTokenExpired` |
| Policies | **G-1** — implementar o justificar por ADR |

### 4.3 Full Stack Testing (20%) — las 8 capas

| Capa | Exigencia | Estado |
|---|---|---|
| 1. Unit | Servicios, validaciones, lógica | cumple — 284 tests |
| 2. Integration | Testcontainers: **BD real, Keycloak**, integraciones | parcial — BD sí (y desde ENV-1, también contra la base desplegada), **Keycloak no** (TEST-1) |
| 3. API / Contract | Endpoints, contratos OpenAPI, status codes, payloads | parcial — Postman sin CI (TEST-3), RestAssured sin uso (TEST-2) |
| 4. E2E | Snapshots, flujos, navegación, **roles**, seguridad, **responsive** | **no se ejecuta en CI** (C-1, TEST-7/8/9) |
| 5. Security | ZAP, JWT, permisos, CORS, Dependency Check/Snyk, autenticación | parcial — ZAP baseline sí; faltan T-5, TEST-11 |
| 6. Performance | Stress, load, usuarios concurrentes, tiempo de respuesta, throughput | **cero** (T-3) |
| 7. Data | Migraciones, integridad, **duplicados**, constraints, seeds | parcial (DATA-1/2, E-1) |
| 8. Exploratory | Charters, bugs encontrados, escenarios | parcial — 2 informes, faltan charters (T-6) |

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

| Métricas exigidas | CPU, Memoria, JVM, Latencia, Throughput, Error rate, DB pool | cumple (CPU y memoria de host desde OBS-3) |
|---|---|---|
| **Logs** | traceId, spanId, correlationId, nivel, usuario, endpoint | **6 de 6** — [informe](testing/reportes/OBS-4-logs-loki.md); solo en perfil `staging`/`prod` |
| **Trazas** | request, database, external calls, errores distribuidos | **3 de 4** — falta verificar errores distribuidos |
| **Alertas** | CPU, error rate, latencia, servicios caídos, fallos de autenticación | **5 de 5** — dos verificadas disparando; +1 de negocio (`ProductosBajoMinimo`) |
| **Métricas de negocio** | [criterio propio] movimientos, unidades, alertas y productos bajo mínimo | **4 series** — [informe](testing/reportes/OBS-2-E-3-metricas-de-negocio.md) |

### 4.5 CI/CD (15%)

El enunciado exige **10 etapas** de pipeline: Checkout, Build, Unit tests, Integration tests, API tests, E2E tests, Security scan, Quality gates, Docker build, Deployment.

| Etapa | GitHub Actions | Jenkins |
|---|---|---|
| Checkout | sí | sí |
| Build | sí | sí |
| Unit tests | sí | sí |
| Integration tests | sí | sí |
| API tests | staging.yml | sí (smoke) |
| E2E tests | **no** | **no** |
| Security scan | staging.yml (ZAP) | **no** |
| Quality gates | JaCoCo; falta Sonar | **no** |
| Docker build | sí | sí |
| Deployment | staging.yml | sí |

Jenkins tiene 8 etapas; faltan E2E, security scan y quality gate (**C-4**).

### 4.6 Calidad de código (10%)

Exige SonarQube o SonarCloud midiendo Coverage, Bugs, Vulnerabilities, Code smells y Duplicación. **Nunca se ha ejecutado** (Q-1). Spotless está configurado y desactivado en todos los pipelines (Q-2).

### 4.7 Repositorio y buenas prácticas

| Requisito | Estado |
|---|---|
| Repositorio público | cumple |
| README profesional | cumple — rutas reales, matriz rol→scopes y el `scope` obligatorio del token |
| **Issues** | 13 issues, todos épicas de fase; **ningún bug** (T-6) |
| Pull Requests | cumple — #30 a #40 |
| Branch strategy | cumple — `main` protegida, 2 checks obligatorios |
| Conventional Commits | cumple — commitlint activo |
| Code Reviews | **riesgo** — 6 de 10 PRs previas sin revisión (BP-1) |
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

> **Decisión tomada:** el stack de la demo se levanta con `SPRING_PROFILES_ACTIVE=staging`. Es el único perfil, junto con `prod`, que emite JSON estructurado, y sin él el panel de logs no puede filtrar por usuario ni por endpoint. Verificado con los seis campos MDC en Loki ([informe P-2](testing/reportes/P-2-capturas-de-evidencia.md)).
>
> **Contrapartida sin resolver:** `application-staging.yml` fija `app.cors.allowed-origins` a `https://staging.inventory.example.com`, así que el frontend de `localhost:3000` queda bloqueado por CORS. Para P-2 bastó un override en `.env`, pero **P-1 y P-3 necesitan la interfaz**. Hay que decidir entre añadir el origen local a `staging`, crear un perfil `demo`, o asumir `dev` y perder el filtrado de logs. Ver [Ola 7](#ola-7--deuda-abierta-por-los-hallazgos).

El área queda cerrada: el pendiente que arrastraba (P-2) está hecho.

### Ola 3 — Capas de testing ausentes (≈14 h)

| # | Acción | Esfuerzo | Capa |
|---|---|---|---|
| **C-1 + TEST-7** | Playwright en `staging.yml` con los 4 usuarios | 3 h | 4 |y
| **TEST-9** | Responsive: 375 / 768 / 1440 px | 45 min | 4 |
| **TEST-8** | `toHaveScreenshot()` en dashboard, productos y stock | 1 h | 4 |
| **TEST-1** | `dasniko/testcontainers-keycloak` + IT con token real — **obligatorio** | 3 h | 2 |
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

### Ola 5 — Documentación (≈10 h restantes) · EN CURSO

| Documento | Exigencia del PDF | Esfuerzo | Estado |
|---|---|---|---|
| `docs/requisitos/` | "documento detallado de requisitos funcionales y no funcionales" | 3 h | pendiente |
| `docs/arquitectura/` | "diagramas de arquitectura, guías de instalación y manuales de mantenimiento" | 3 h | pendiente |
| `docs/operacion/manual-mantenimiento.md` | idem + **la trampa del volumen de Keycloak** | 2 h | pendiente |
| `docs/testing/guia-de-pruebas.md` | "casos de prueba, resultados y cualquier defecto encontrado" | 2 h | pendiente |
| **T-6** | Charters y bugs como **issues de GitHub** | 1 h | pendiente — hay material de sobra: los informes de QA más los hallazgos de la Ola 4 |
| **D-1…D-4** | README: stack real, badge, rutas correctas | 45 min | **hecho** — declaraba `Base URL: /api/v1`, que no existe, y un `client_id` y unos usuarios que tampoco. Corregido con las rutas reales, la matriz rol→scopes y el aviso del `scope` obligatorio, sin el cual toda petición responde 403. `CONTRIBUTING` alineado con los prefijos de rama que se usan de verdad |

> **T-6 tiene material listo.** Los informes de [G-6](testing/reportes/G-6-escalada-de-scopes.md) y [G-4/G-5](testing/reportes/G-4-G-5-scopes-por-rol.md) son sesiones exploratorias con reproducción. Convertirlos en issues cierra la ausencia total de issues de tipo bug, que es evaluable.

### Ola 6 — Presentación final (5%, ≈3 h) · EN CURSO

Es un entregable explícito: *"presentación final funcional del sistema en clase"*.

| # | Acción | Esfuerzo | Estado |
|---|---|---|---|
| **P-1** | Guion de demo: alta de producto → movimiento de stock → alerta → auditoría → dashboard | 1 h | pendiente |
| **P-2** | Capturas de los 4 dashboards con datos reales y de una alerta disparada | 1 h | **hecho** — 6 capturas, 34 paneles sin ninguno vacío, alerta verificada hasta Alertmanager, [informe](testing/reportes/P-2-capturas-de-evidencia.md) |
| **P-3** | Ensayo con el stack levantado desde cero (`down -v && up`) | 1 h | pendiente — **bloqueado** por el CORS de `staging` |

> **Dos avisos de P-2 que afectan al guion de P-1.** Los paneles de Negocio usan `increase()`: si en la demo se encadenan todos los movimientos seguidos saldrán en cero, porque Prometheus no puede medir el incremento del primer punto de una serie. Hay que espaciarlos o levantar el stack con antelación. Y la ventana temporal de los dashboards no debe abarcar un reinicio del backend con otro perfil, o cada panel duplica sus series.

### Ola 7 — Deuda abierta por los hallazgos (≈4 h)

| # | Acción | Esfuerzo |
|---|---|---|
| **G-3a** | Unión de scopes en `AuthContext.tsx`; hoy replica el bug de primer-rol-gana | 45 min |
| **G-8** | `scopeMappings` en Keycloak: corrección de raíz de G-6 y prerrequisito de G-2 | 1 h |
| **S-4b** | Quitar `JWT_SECRET` y `JWT_EXPIRATION_MS` de `staging.yml` | 10 min |
| **ADR-001** | Por qué el mapa rol→scopes vive en Java | 30 min |
| **P-2a** | CORS de `staging` apunta a `staging.inventory.example.com`: bloquea el frontend local y deja P-3 sin interfaz | 20 min |
| **P-2b** | `keycloak-init` no es idempotente: al reejecutarse sobre un realm existente lanza `duplicate key … uk_cli_scope` y ensucia el panel de eventos | 30 min |
| **TEST-10b** | El realm emite tokens de 300 s y el escaneo activo de ZAP puede durar más. Al caducar, el resto de la API se recorre sin autenticar y el escaneo aprueba precisamente por no encontrar nada. Ya hay un paso que lo detecta y falla; falta la corrección de raíz: un cliente de Keycloak dedicado al escaneo con `accessTokenLifespan` mayor | 45 min |

### Ola 8 — Alto coste, decidir explícitamente

| # | Acción | Esfuerzo | Alternativa |
|---|---|---|---|
| **G-1** | Authorization Services: Resources, Policies, Permissions — **"Policies" está nombrado en el enunciado** | 5 h | ADR argumentando que scopes + roles cubren el modelo |
| **G-2** | Mover rol→permisos a Keycloak (depende de G-8) | 4 h | ADR-001 |
| **A-2 / M-1** | `UserController` sobre la Admin API — daría uso a `user:manage` | 4 h | ADR delegando a la consola |
| **A-1** | Unificar rutas bajo `/api/v1` | 3 h | **Riesgo alto** cerca de la entrega; corregir el README como mínimo |

### Mejoras funcionales (≈5 h)

| # | Acción | Esfuerzo |
|---|---|---|
| **D-1** | "Productos más vendidos" agregando movimientos `OUT` — **exigido en el dashboard** | 1,5 h |
| **D-2** | Listar productos críticos — **exigido en el dashboard** | 45 min |
| **F-2** | Ordenamiento en la tabla de productos — **exigido en el alcance** | 1 h |
| D-3 | `topProducts` con query en BD | 45 min |
| M-2 | `GET /api/stock/{productId}` con `stock:view` | 30 min |
| F-1 | ADR-002 sobre el soft delete | 30 min |
| D-4 | `@axe-core/playwright` en E2E | 1 h |
| E-2 | Validación condicional de `quantity` | 45 min |
| **SEC-2** | `onTokenExpired` — **"expiración de sesiones" es obligatorio** | 45 min |
| **S-2** | Test con `grant_type=refresh_token` — **"refresh tokens" es obligatorio** | 30 min |
| — | ~~**[criterio propio]** Métricas de login de Keycloak vía SPI~~ — **descartada**: Keycloak sí emite `LOGIN_ERROR` con usuario, IP y motivo al log, y Loki lo indexa. Ya visible en el dashboard de Seguridad ([informe](testing/reportes/OBS-dashboards.md)) | — |

---

## 6. Qué hacer con el tiempo que quede

**8 horas:** Loki + OBS-4 + OBS-2/E-3 + los 4 dashboards.
Cierra Observabilidad por completo, que es el mayor déficit y el bloque más defendible en la presentación.

**20 horas:** lo anterior + Ola 2 completa + C-1/TEST-7 + TEST-1 + T-3 + Ola 6 (presentación).
Deja Observabilidad y Testing por encima del 85 % y cubre las tres capas de testing en cero.

**40 horas:** todo salvo la Ola 8, más la Ola 5 completa.

> **Reservar siempre las últimas 3 horas para la Ola 6.** Vale 5 % y hoy está en cero; es el único bloque donde no hacer nada cuesta la nota íntegra del área.

---

## 7. Reglas de trabajo

1. **Contrastar contra el PDF antes de decidir alcance.** Dos decisiones de esta sesión se tomaron sobre el análisis de brechas en lugar del enunciado, y una de ellas casi cuesta 1,5 h en una integración no exigida.
2. **Medir sobre build limpio.** Una medición de cobertura sobre un `target/` obsoleto dio 85,5 % cuando el valor real era 71,6 %. La cifra que vale es la del artefacto de CI.
3. **Nada de configuración decorativa.** Van tres casos encontrados: el trigger a una rama muerta, el check de CI que no ejecutaba nada y el secreto de un cliente `bearerOnly`. Si algo no se ejecuta, o se conecta o se borra.
4. **Cada corrección con su evidencia**, archivada en `docs/testing/reportes/`.
5. **Reviews reales.** Cada PR con aprobación del otro integrante. Es evaluable y sale gratis.
6. **Participación equitativa.** El enunciado evalúa *"commits individuales, ramas, issues y pull requests"* de ambos. Repartir las olas explícitamente.
7. **Los bugs van a Issues.** Hay 13 issues y ninguno es un bug, pese a haberse encontrado y corregido varios documentados.
