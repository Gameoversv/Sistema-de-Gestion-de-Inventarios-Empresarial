# Plan de Ejecución Priorizado

**Base:** [ANALISIS_BRECHAS.md](ANALISIS_BRECHAS.md) — 47 hallazgos verificados contra `Proyecto_Final_V3.pdf`
**Fecha:** 2026-07-21
**Criterio de orden:** puntos de evaluación recuperados por hora de trabajo, respetando dependencias técnicas.

---

## Situación de partida

| Área | Peso | Actual | Alcanzable | Δ |
|---|---|---|---|---|
| Funcionalidad | 15% | ~85% | ~98% | +2,0 pts |
| Testing | 20% | ~60% | ~90% | +6,0 pts |
| Seguridad | 10% | ~70% | ~90% | +2,0 pts |
| Observabilidad | 15% | ~30% | ~85% | +8,3 pts |
| CI/CD | 15% | ~60% | ~90% | +4,5 pts |
| Calidad de código | 10% | ~35% | ~85% | +5,0 pts |
| Documentación | 10% | ~25% | ~90% | +6,5 pts |
| **Total** | | **~55%** | **~89%** | **+34 pts** |

Esfuerzo total estimado: **60–75 horas**. Las primeras 12 horas recuperan cerca de la mitad de la brecha.

---

## OLA 0 — Quick wins (≈2,5 h, impacto desproporcionado)

Todo aquí son ediciones de una línea o de configuración. Hacer primero, en un solo bloque.

| # | Acción | Archivo | Esfuerzo |
|---|---|---|---|
| **CI-1** | Añadir `main` a los triggers de `staging.yml` y ejecutarlo | `.github/workflows/staging.yml` | 15 min |
| **CI-3** | Activar `required_status_checks` en la protección de `main` | GitHub Settings | 10 min |
| **TEST-5** | `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` en failsafe | `backend/pom.xml` | 10 min |
| **TEST-6** | Corregir excludes de JaCoCo → `com/inventory/common/config/**` | `backend/pom.xml` | 10 min |
| **ENV-2** | Jenkins: `./mvnw verify` en la etapa Integration Tests | `Jenkinsfile` | 15 min |
| **G-7** | Eliminar `scripts/keycloak/realm-export.json` (copia muerta) | — | 10 min |
| **BP-2** | `"secret": "${KC_BACKEND_CLIENT_SECRET}"` en el realm export | `keycloak/realm-export.json` | 20 min |
| **S-4** | Quitar `JWT_SECRET` y `JWT_EXPIRATION_MS` residuales | `.env.example`, `docker-compose.yml` | 10 min |
| **A-3** | Correo de equipo en lugar del personal en OpenAPI | `OpenApiConfig.java` | 5 min |
| **DOC-2** | `mvn verify -P generate-docs` → commitear `docs/api/openapi.yaml` | — | 15 min |
| **BP-3** | Alinear `CONTRIBUTING.md` con el flujo real (`main`) | `CONTRIBUTING.md` | 15 min |
| **CI-4** | Borrar ramas `backup-*` y `feature/*` ya mergeadas | GitHub | 20 min |

> **CI-1 es la acción de mayor retorno del plan.** El pipeline de staging lleva parado desde el 2026-06-05: reconectarlo revalida de golpe todo lo mergeado después (SPA, E2E, ITs, Jenkins, Grafana) y probablemente destape SEC-1 y G-6 sin trabajo adicional. Ejecutarlo **antes** de planificar el resto, porque sus resultados cambian prioridades.

---

## OLA 1 — Bugs verificados (≈4 h)

Defectos reales, no ausencias. Baratos y defienden Seguridad y Funcionalidad.

| # | Acción | Esfuerzo | Nota |
|---|---|---|---|
| **G-5** | Fallback de `permittedScopesForRoles` → `Set.of()` (default-deny) | 15 min | + test |
| **G-4** | Devolver la **unión** de scopes de todos los roles, no el primero | 30 min | + test multi-rol; replicar en `AuthContext.tsx` |
| **G-6** | `docker compose down -v && up` y decodificar el token emitido | 30 min | Si faltan scopes → declararlos default o pedirlos en `login({ scope })` (+1 h) |
| **SEC-1** | `VITE_API_BASE_URL` vacío en staging (usar proxy nginx) o CORS con origen real | 20 min | Lo confirma la ejecución de CI-1 |
| **TEST-4** | `./mvnw verify` en CI como paso bloqueante | 15 min | Fallará: es el objetivo |
| **E-1** | Migración V8: backfill + `SET NOT NULL` en columnas de snapshot | 30 min | Elimina los `"null"` del CSV |
| **F-3** | Decidir y validar si `categoryId` es obligatorio | 15 min | |
| **DATA-3** | Eliminar tabla `users` y entidad `User` muertas (o justificar) | 45 min | |

---

## OLA 2 — Observabilidad (≈12 h, +8,3 pts — mayor déficit ponderado)

**Orden obligatorio:** OBS-3 va primero. Sin exporters, dos de los cuatro dashboards y una de las cinco alertas no tienen datos posibles.

| # | Acción | Esfuerzo |
|---|---|---|
| **OBS-3** | Añadir `node-exporter`, `postgres-exporter` y scrape de Keycloak (`KC_METRICS_ENABLED`) a compose + `prometheus.yml` | 1,5 h |
| **OBS-5** | `observability/prometheus/rules/alerts.yml` con las 5 alertas + Alertmanager en compose | 2 h |
| **OBS-2** + **E-3** | `Counter` `inventory_stock_alerts_total{sku}` y contadores de movimientos por tipo | 1,5 h |
| **OBS-1** | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`; `management.otlp.tracing.endpoint` | 2 h |
| — | Alloy, Tempo y Loki en `docker-compose.yml` con sus configs | 2 h |
| **OBS-4** | Filtro MDC con `correlationId`, usuario (`sub`) y endpoint + patrón logback con `traceId`/`spanId` | 1,5 h |
| **OBS-6** | Datasources `tempo.yml` y `loki.yml`, con correlación traces↔logs | 30 min |
| — | Separar en 4 dashboards: Infraestructura, Aplicación, Negocio y **Seguridad** | 3 h |

Al terminar: levantar el stack completo y **capturar evidencia** (capturas con datos reales) para la presentación y la documentación.

---

## OLA 3 — Capas de testing ausentes (≈14 h, +6 pts)

| # | Acción | Esfuerzo | Capa |
|---|---|---|---|
| **C-1** + **TEST-7** | Playwright en `staging.yml` con los 4 usuarios (`inv_admin`, `inv_clerk`, `inv_auditor`, `inv_viewer`) | 3 h | 4 |
| **TEST-9** | Proyectos responsive: 375 / 768 / 1440 px | 45 min | 4 |
| **TEST-8** | `toHaveScreenshot()` en dashboard, productos y stock | 1 h | 4 |
| **T-3** | Suite k6: `load`, `stress`, `spike` con umbrales `p(95)<500ms`, `http_req_failed<1%` | 3 h | 6 |
| **TEST-1** | `dasniko/testcontainers-keycloak` + IT con token real | 3 h | 2 |
| **T-5** | OWASP Dependency Check (backend) y `npm audit`/Snyk (frontend) en CI | 45 min | 5 |
| **TEST-3** + **TEST-2** | Newman en CI + dar uso real a RestAssured para validación de contrato OpenAPI | 2 h | 3 |
| **TEST-11** | Test de CORS por perfil (`Access-Control-Allow-Origin`) | 30 min | 5 |
| **DATA-1/2** | Tests de CHECK constraints y verificación de seeds | 1,5 h | 7 |

> **C-1 + TEST-7 es la segunda mejor palanca del plan:** cierra un sub-requisito de la capa 4, ejercita G-4 y G-5, detecta SEC-1 y aporta la evidencia de "E2E contra sistema desplegado" que exige el bloque de Entornos.

---

## OLA 4 — Calidad y CI/CD (≈8 h, +9,5 pts combinados)

| # | Acción | Esfuerzo |
|---|---|---|
| **Q-1** | Job de SonarCloud con `SONAR_TOKEN` + `sonar.projectKey` + badge | 1,5 h |
| **Q-3** | Job de frontend en CI: `npm ci && npm run lint && npm run test:coverage` | 45 min |
| **Q-2** | Retirar `-Dspotless.check.skip=true` de los 8 puntos; `mvn spotless:apply` una vez | 45 min |
| **Q-4** | Publicar cobertura (artefacto + badge real) | 30 min |
| **ENV-1** | IT que reciba la URL de BD por configuración externa (sin `@DynamicPropertySource`) | 1 h |
| **C-4** | Igualar etapas del `Jenkinsfile`: security scan, quality gate, E2E | 2 h |
| **CI-2** | Crear tag `v1.0.0` y ejecutar `production.yml` por primera vez | 15 min |
| — | Smoke test post-release en `production.yml` | 45 min |
| **TEST-10** | ZAP autenticado (context + token) o `zap-full-scan` con umbral | 2 h |

Al subir cobertura de ramas de 71% a ≥80% (necesario para TEST-4): priorizar ramas no cubiertas en `ProductServiceImpl`, `StockServiceImpl` y `GlobalExceptionHandler`.

---

## OLA 5 — Documentación (≈12 h, +6,5 pts)

La peor relación esfuerzo/nota del plan es dejarla sin hacer: es el 10% con menor coste técnico.

| Documento | Contenido mínimo | Esfuerzo |
|---|---|---|
| `docs/requisitos/requisitos-funcionales-y-no-funcionales.md` | RF por módulo + RNF (rendimiento, seguridad, disponibilidad, mantenibilidad) | 3 h |
| `docs/arquitectura/` | Diagramas en Mermaid: componentes, despliegue, ERD, secuencia OAuth2 | 3 h |
| `docs/operacion/manual-mantenimiento.md` | Backups, migraciones, rotación de secretos, runbooks de las 5 alertas | 2 h |
| `docs/testing/guia-de-pruebas.md` | Estrategia por capa, casos, resultados y defectos | 2 h |
| **T-6** | `docs/testing/exploratory/`: charters, notas de sesión, bugs | 2 h |
| **D-1…D-4** | Corregir README: stack real, badge, ejemplos de `@PreAuthorize`, `description` de `package.json` | 45 min |

> **T-6 tiene material listo.** Convertir en issues de GitHub los hallazgos ya verificados —G-4, G-5, SEC-1, E-1, D-1— aporta trazabilidad real y resuelve de paso la ausencia total de issues de tipo bug.

---

## OLA 6 — Alto coste, decidir explícitamente (≈15 h)

Ninguno es imprescindible para aprobar bien. Evaluar tiempo restante antes de entrar.

| # | Acción | Esfuerzo | Alternativa barata |
|---|---|---|---|
| **G-2** + **G-3** | Mover rol→permisos a Keycloak (roles compuestos) y eliminar la duplicación Java/TS | 5 h | ADR justificando la decisión actual (30 min) |
| **G-1** | Keycloak Authorization Services: Resources + Policies + Permissions | 5 h | ADR explicando que los scopes cubren el modelo (30 min) |
| **A-2** / **M-1** | `UserController` sobre la Admin API de Keycloak con `SCOPE_user:manage` | 4 h | ADR delegando la gestión a la consola (30 min) |
| **A-1** | Unificar rutas bajo `/api/v1` | 3 h | **Riesgo alto**: toca backend, nginx, Postman, frontend y ambos pipelines |

> **Sobre A-1:** o se hace en la Ola 0–1, o no se hace. Un refactor de rutas cerca de la entrega rompe la demo. Si no se aborda, es obligatorio al menos corregir el README, que hoy documenta rutas `/api/v1` inexistentes y cuyos ejemplos `curl` fallan.

> **Sobre G-1/G-2/A-2:** las tres alternativas baratas son ADRs. Un ADR que argumente una decisión técnica puntúa mucho mejor que un requisito silenciosamente ausente.

---

## Mejoras funcionales (≈5 h, +2 pts — encajar donde quepa)

| # | Acción | Esfuerzo |
|---|---|---|
| **D-1** | `metric=sold` agregando movimientos `OUT` → panel "Productos más vendidos" | 1,5 h |
| **F-2** | Headers de tabla ordenables en `ProductsPage` | 1 h |
| **D-2** | Listar productos críticos en el dashboard (endpoint ya existe) | 45 min |
| **D-3** | `topProducts` con query ordenada en BD en lugar de `findAll()` en memoria | 45 min |
| **M-2** | `GET /api/stock/{productId}` protegido con `stock:view` | 30 min |
| **F-1** | ADR-002 justificando el soft delete + renombrar a "Desactivar" en Swagger/UI | 30 min |
| **D-4** | `@axe-core/playwright` en la suite E2E | 1 h |
| **E-2** | Validación condicional de `quantity` según tipo de movimiento | 45 min |
| **SEC-2** | `keycloak.onTokenExpired` + serialización de reintentos | 45 min |
| **S-2** | Test de API con `grant_type=refresh_token` | 30 min |

---

## Escenarios por tiempo disponible

**Si solo hay 8 horas:** Ola 0 completa + Ola 1 completa + OBS-3 + OBS-5 + Q-1 + Q-3.
Recupera ~12 puntos: arregla los bugs reales, reactiva el pipeline, activa Sonar y deja las alertas funcionando.

**Si hay 20 horas:** lo anterior + Ola 2 completa + C-1/TEST-7 + T-3 (k6) + T-6 (exploratory).
Recupera ~22 puntos y elimina las dos capas de testing en cero.

**Si hay 40 horas:** todo salvo la Ola 6, más la Ola 5 completa.
Recupera ~31 puntos y deja las siete áreas por encima del 80%.

---

## Reglas de trabajo para el resto del proyecto

1. **BP-1 — reviews reales desde ahora.** Seis de las diez últimas PRs se mergearon sin ninguna revisión. Con dos integrantes, cada PR debe llevar aprobación del otro. Es evaluable y sale gratis.
2. **Participación equitativa.** Las 10 PRs recientes son de un solo autor. Repartir explícitamente las olas entre ambos integrantes y que se refleje en commits y PRs.
3. **Nada de configuración decorativa.** Si algo no se ejecuta, o se conecta o se borra. Los `management.tracing` sin dependencias, RestAssured sin uso y Sonar sin job puntúan peor que su ausencia en una materia de aseguramiento de calidad.
4. **Cada corrección con su evidencia.** Captura, reporte o test que la respalde, archivado en `docs/testing/reportes/`.
