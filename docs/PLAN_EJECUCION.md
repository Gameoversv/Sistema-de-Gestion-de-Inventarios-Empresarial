# Plan de Ejecución Priorizado

**Base:** [ANALISIS_BRECHAS.md](ANALISIS_BRECHAS.md) — 47 hallazgos verificados contra `Proyecto_Final_V3.pdf`
**Creado:** 2026-07-21 · **Actualizado:** 2026-07-22 tras ejecutar las Olas 0 y 1
**Criterio de orden:** puntos de evaluación recuperados por hora de trabajo, respetando dependencias técnicas.

---

## Situación

| Área | Peso | Inicial | Actual | Alcanzable |
|---|---|---|---|---|
| Funcionalidad | 15% | ~85% | ~85% | ~98% |
| Testing | 20% | ~60% | ~78% | ~90% |
| Seguridad | 10% | ~70% | ~85% | ~90% |
| Observabilidad | 15% | ~30% | ~30% | ~85% |
| CI/CD | 15% | ~60% | ~85% | ~90% |
| Calidad de código | 10% | ~35% | ~45% | ~85% |
| Documentación | 10% | ~25% | ~40% | ~90% |

Salvo la cobertura, que está medida, los porcentajes son estimaciones.

**Cobertura real** (artefacto de JaCoCo en GitHub Actions, no medición local):

| Contador | Antes | Ahora | Umbral |
|---|---|---|---|
| BRANCH | 71,6 % (141/197) | **83,2 %** (164/197) | 80 % |
| LINE | 84,9 % (657/774) | **91,3 %** (707/774) | 80 % |

---

## Lo que cambió respecto al plan original

Cinco hallazgos de la ejecución alteran las prioridades. Merecen leerse antes de seguir el orden de las olas.

### 1. El mapa rol→scopes de Java es el único control de seguridad

Verificado en vivo ([informe](testing/reportes/G-6-escalada-de-scopes.md)): Keycloak emite **cualquier scope a cualquier usuario autenticado**, sin comprobar el rol. `inv_viewer` obtuvo `product:manage`, `stock:manage`, `user:manage` y `audit:view`.

Los scopes se enganchan como opcionales sin `scopeMappings`, y `inventory-frontend` es cliente público con `directAccessGrantsEnabled`.

**Consecuencia sobre G-2 y G-3 (Ola 6):** la idea de "mover rol→permisos a Keycloak y eliminar la duplicación Java/TS" no puede ejecutarse en ese orden. Retirar el mapa antes de restringir los scopes en el IdP abre la escalada. El orden correcto es: primero `scopeMappings` en Keycloak, después simplificar el backend. Se añade **G-8** para cubrir la corrección de raíz.

### 2. La cobertura ya pasa el umbral

El plan asumía 71 % de ramas y reservaba trabajo de la Ola 4 para subirla. Tras cubrir `UnifiedAuditService` (de 0/24 a 23/24), el gate pasa con 83,2 %. Ese trabajo se reduce a mantener el margen.

### 3. Había un check de CI que no ejecutaba nada

El job `Integration Tests (Testcontainers)` invocaba goals de failsafe sueltos, sin fase previa que compilara los tests: terminaba en `No tests to run` y daba verde en 12 segundos. Tres ITs existentes no se ejecutaban ni en GitHub Actions ni en Jenkins. Corregido; ahora ambos checks son obligatorios en `main`.

### 4. BP-2 se resolvió al revés de lo previsto

El plan proponía parametrizar el secreto del realm. El cliente `inventory-backend` es `bearerOnly`: no ejecuta ningún flujo de autenticación, Keycloak descarta el secreto del import y ningún código lee la variable. Se eliminó en lugar de parametrizarse.

### 5. El stack no arranca contra un volumen de Keycloak preexistente

`keycloak-init` falla con `failed to obtain admin token`: Keycloak solo crea el admin bootstrap con la BD vacía, así que un volumen viejo conserva la contraseña de la primera ejecución. Se resuelve borrando `keycloak_db_data`. Debe entrar en el manual de operación de la Ola 5.

---

## Ola 0 — Quick wins · COMPLETADA

| # | Acción | Estado |
|---|---|---|
| CI-1 | `main` en los triggers de `staging.yml` | hecho (#30) |
| CI-3 | `required_status_checks` en la protección de `main` | hecho — 2 checks obligatorios |
| CI-4 | Borrar ramas mergeadas | hecho — 13 ramas |
| TEST-5 | `@{argLine}` en failsafe | hecho (#31) |
| TEST-6 | Excludes de JaCoCo al paquete real | hecho (#31) |
| ENV-2 | `./mvnw verify` en Jenkins | hecho (#31) |
| G-7 | Eliminar la copia muerta del realm export | hecho (#31) |
| BP-2 | Secreto hardcodeado del realm | hecho (#33) — eliminado, no parametrizado |
| A-3 | Correo institucional en OpenAPI | hecho (#31) |
| DOC-2 | `docs/api/openapi.yaml` | hecho (#33) — requirió corregir un 401 en `/v3/api-docs.yaml` |
| BP-3 | `CONTRIBUTING.md` sobre `main` | hecho (#31) |
| **S-4** | Variables `JWT_` residuales | **parcial** — falta `staging.yml:38-39` |

> CI-1 fue la acción de mayor retorno, como anticipaba el plan: destapó que `main` no compilaba y que el pipeline llevaba parado desde el 2026-06-05.

---

## Ola 1 — Bugs verificados · CASI COMPLETA

| # | Acción | Estado |
|---|---|---|
| TEST-4 | `./mvnw verify` como paso bloqueante en CI | hecho (#32) |
| G-5 | Denegar por defecto en `permittedScopesForRoles` | hecho (#33) |
| G-4 | Unión de scopes de todos los roles | hecho (#33) |
| G-6 | Decodificar el token emitido | hecho (#33) — escalada confirmada |
| SEC-1 | `VITE_API_BASE_URL` en staging | **revisar** — el pipeline pasa, pero valida por API, no por navegador. Sin evidencia de que la SPA funcione |
| **E-1** | Migración V8: backfill + `SET NOT NULL` en columnas de snapshot | pendiente · 30 min |
| **F-3** | Decidir si `categoryId` es obligatorio | pendiente · 15 min |
| **DATA-3** | Eliminar tabla `users` y entidad `User` muertas | pendiente · 45 min |

---

## Ola 1b — Deuda abierta por los hallazgos (≈2 h)

Trabajo nuevo que no existía en el plan original.

| # | Acción | Esfuerzo |
|---|---|---|
| **G-3a** | Replicar la unión de scopes en `AuthContext.tsx`. Hoy duplica la tabla con lógica de primer-rol-gana: un usuario multi-rol verá opciones que el backend rechaza | 45 min |
| **G-8** | `scopeMappings` en Keycloak que aten cada client scope a los roles autorizados. Corrección de raíz de G-6 y prerrequisito de G-2 | 1 h |
| **S-4b** | Quitar `JWT_SECRET` y `JWT_EXPIRATION_MS` de `staging.yml` | 10 min |
| **ADR-001** | Por qué el mapa rol→scopes vive en Java. Con G-6 documentado, el argumento es sólido | 30 min |

---

## Ola 2 — Observabilidad (≈12 h, +8,3 pts) · MAYOR DÉFICIT

Sin tocar. Ahora es el área con más puntos por recuperar y la única que sigue en su valor inicial.

**Orden obligatorio:** OBS-3 primero. Sin exporters, dos de los cuatro dashboards y una de las cinco alertas no tienen datos posibles.

| # | Acción | Esfuerzo |
|---|---|---|
| OBS-3 | `node-exporter`, `postgres-exporter` y scrape de Keycloak (`KC_METRICS_ENABLED`) en compose + `prometheus.yml` | 1,5 h |
| OBS-5 | `observability/prometheus/rules/alerts.yml` con las 5 alertas + Alertmanager | 2 h |
| OBS-2 + E-3 | `Counter` `inventory_stock_alerts_total{sku}` y contadores de movimientos por tipo | 1,5 h |
| OBS-1 | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` | 2 h |
| — | Alloy, Tempo y Loki en compose con sus configs | 2 h |
| OBS-4 | Filtro MDC con `correlationId`, usuario (`sub`) y endpoint + patrón logback con `traceId`/`spanId` | 1,5 h |
| OBS-6 | Datasources `tempo.yml` y `loki.yml` con correlación traces↔logs | 30 min |
| — | Separar en 4 dashboards: Infraestructura, Aplicación, Negocio y Seguridad | 3 h |

Al terminar: levantar el stack y **capturar evidencia con datos reales** para la presentación.

---

## Ola 3 — Capas de testing ausentes (≈13 h)

| # | Acción | Esfuerzo | Capa |
|---|---|---|---|
| C-1 + TEST-7 | Playwright en `staging.yml` con los 4 usuarios | 3 h | 4 |
| TEST-9 | Proyectos responsive: 375 / 768 / 1440 px | 45 min | 4 |
| TEST-8 | `toHaveScreenshot()` en dashboard, productos y stock | 1 h | 4 |
| T-3 | Suite k6: `load`, `stress`, `spike` con `p(95)<500ms`, `http_req_failed<1%` | 3 h | 6 |
| TEST-1 | `dasniko/testcontainers-keycloak` + IT con token real | 3 h | 2 |
| T-5 | OWASP Dependency Check y `npm audit`/Snyk en CI | 45 min | 5 |
| TEST-3 + TEST-2 | Newman en CI + RestAssured para contrato OpenAPI | 2 h | 3 |
| TEST-11 | Test de CORS por perfil | 30 min | 5 |
| DATA-1/2 | Tests de CHECK constraints y verificación de seeds | 1,5 h | 7 |

> **C-1 + TEST-7 sigue siendo la mejor palanca disponible.** Cierra la capa 4, ejercita G-4 y G-5 desde el navegador y aporta la evidencia de "E2E contra sistema desplegado". Además es lo único que confirmaría SEC-1.
>
> **TEST-1 baja de prioridad:** los tres ITs con Testcontainers ya se ejecutan en CI desde #32.

---

## Ola 4 — Calidad y CI/CD (≈6 h)

| # | Acción | Esfuerzo |
|---|---|---|
| Q-1 | Job de SonarCloud con `SONAR_TOKEN` + `sonar.projectKey` + badge | 1,5 h |
| Q-3 | Job de frontend en CI: `npm ci && npm run lint && npm run test:coverage` | 45 min |
| Q-2 | Retirar `-Dspotless.check.skip=true` de los 8 puntos; `mvn spotless:apply` una vez | 45 min |
| Q-4 | Publicar cobertura (artefacto + badge real) | 30 min |
| ENV-1 | IT que reciba la URL de BD por configuración externa | 1 h |
| C-4 | Igualar etapas del `Jenkinsfile`: security scan, quality gate, E2E | 2 h |
| CI-2 | Crear tag `v1.0.0` y ejecutar `production.yml` por primera vez | 15 min |
| — | Smoke test post-release en `production.yml` | 45 min |
| TEST-10 | ZAP autenticado o `zap-full-scan` con umbral | 2 h |

> El trabajo de subir cobertura ya no es necesario: el gate pasa con 83,2 %. Si se quiere margen, los huecos son `EnversRevisionListener` (8 ramas), `ProductAuditService` (6) y `SecurityConfig` (5).

---

## Ola 5 — Documentación (≈12 h, +6,5 pts)

La peor relación esfuerzo/nota del plan es dejarla sin hacer.

| Documento | Contenido mínimo | Esfuerzo |
|---|---|---|
| `docs/requisitos/requisitos-funcionales-y-no-funcionales.md` | RF por módulo + RNF | 3 h |
| `docs/arquitectura/` | Mermaid: componentes, despliegue, ERD, secuencia OAuth2 | 3 h |
| `docs/operacion/manual-mantenimiento.md` | Backups, migraciones, rotación de secretos, runbooks de las 5 alertas, **y la trampa del volumen de Keycloak** | 2 h |
| `docs/testing/guia-de-pruebas.md` | Estrategia por capa, casos, resultados y defectos | 2 h |
| T-6 | `docs/testing/exploratory/`: charters, notas, bugs | 1 h |
| D-1…D-4 | README: stack real, badge, ejemplos de `@PreAuthorize`, `description` de `package.json` | 45 min |

> **T-6 tiene material listo y verificado.** Los informes de [G-6](testing/reportes/G-6-escalada-de-scopes.md) y [G-4/G-5](testing/reportes/G-4-G-5-scopes-por-rol.md) son sesiones exploratorias documentadas con reproducción. Convertirlos en issues de GitHub cierra la ausencia de issues de tipo bug.

---

## Ola 6 — Alto coste, decidir explícitamente (≈12 h)

| # | Acción | Esfuerzo | Alternativa barata |
|---|---|---|---|
| G-2 | Mover rol→permisos a Keycloak y eliminar la duplicación Java/TS | 4 h | ADR-001 (30 min) |
| G-1 | Keycloak Authorization Services: Resources + Policies + Permissions | 5 h | ADR explicando que los scopes cubren el modelo (30 min) |
| A-2 / M-1 | `UserController` sobre la Admin API de Keycloak | 4 h | ADR delegando la gestión a la consola (30 min) |
| A-1 | Unificar rutas bajo `/api/v1` | 3 h | **Riesgo alto** cerca de la entrega |

> **G-2 depende de G-8.** No se puede retirar el mapa Java mientras Keycloak no restrinja los scopes por rol.
>
> **Sobre A-1:** o se hizo en la Ola 0, o no se hace. Si no, es obligatorio corregir el README, que documenta rutas `/api/v1` inexistentes con ejemplos `curl` que fallan.
>
> **Nota:** `user:manage` no lo exige ningún `@PreAuthorize`. El scope existe y se concede, pero no protege nada — es el síntoma de que falta A-2.

---

## Mejoras funcionales (≈5 h, +2 pts)

| # | Acción | Esfuerzo |
|---|---|---|
| D-1 | `metric=sold` agregando movimientos `OUT` → "Productos más vendidos" | 1,5 h |
| F-2 | Headers de tabla ordenables en `ProductsPage` | 1 h |
| D-2 | Listar productos críticos en el dashboard | 45 min |
| D-3 | `topProducts` con query ordenada en BD | 45 min |
| M-2 | `GET /api/stock/{productId}` protegido con `stock:view` | 30 min |
| F-1 | ADR-002 sobre el soft delete + renombrar a "Desactivar" | 30 min |
| D-4 | `@axe-core/playwright` en la suite E2E | 1 h |
| E-2 | Validación condicional de `quantity` según tipo de movimiento | 45 min |
| SEC-2 | `keycloak.onTokenExpired` + serialización de reintentos | 45 min |
| S-2 | Test de API con `grant_type=refresh_token` | 30 min |

---

## Qué hacer ahora

**Si quedan 8 horas:** Ola 1b completa + OBS-3 + OBS-5 + Q-1 + Q-3.
Cierra la deuda de seguridad abierta, deja las alertas funcionando y activa Sonar.

**Si quedan 20 horas:** lo anterior + Ola 2 completa + C-1/TEST-7 + T-6.
Ataca el mayor déficit ponderado y elimina la capa de testing en cero.

**Si quedan 40 horas:** todo salvo la Ola 6, más la Ola 5 completa.

---

## Reglas de trabajo

1. **BP-1 — reviews reales.** Seis de las diez PRs anteriores se mergearon sin revisión. Las #30 a #33 han pasado por PR con checks obligatorios; mantener el hábito.
2. **Participación equitativa.** Repartir explícitamente las olas entre ambos integrantes y que se refleje en commits y PRs.
3. **Nada de configuración decorativa.** Ya se han encontrado tres casos: el trigger a una rama muerta, el check de CI que no ejecutaba nada y el secreto de un cliente `bearerOnly`. Si algo no se ejecuta, o se conecta o se borra.
4. **Cada corrección con su evidencia**, archivada en `docs/testing/reportes/`.
5. **Medir sobre build limpio.** Una medición de cobertura sobre un `target/` obsoleto dio 85,5 % cuando el valor real era 71,6 %. La cifra que vale es la del artefacto de CI.
