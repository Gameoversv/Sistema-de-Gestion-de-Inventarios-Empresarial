# Requisitos No Funcionales

Cómo debe comportarse el sistema. Criterios de estado y convenciones en el [README](README.md).

Cada requisito lleva un **criterio de aceptación medible**. Un no funcional que no se puede medir no es un requisito, es una intención.

---

## 1. Seguridad

### RNF-01 — Autenticación con Keycloak, OAuth2 y JWT

| | |
|---|---|
| **Criterio** | Toda ruta de negocio responde **401** sin token válido. El token se valida contra el emisor y su expiración, no solo en firma. |
| **Origen** | *"La autenticación y autorización deberá implementarse utilizando obligatoriamente: Keycloak, OAuth2 y JWT"* |
| **Estado** | **Cumple** |
| **Implementación** | [`SecurityConfig.java:118`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L118) — `NimbusJwtDecoder` con `JwtValidators.createDefaultWithIssuer` + `JwtTimestampValidator`; `anyRequest().authenticated()` |
| **Verificación** | `SecurityIntegrationTest`, `KeycloakJwtConverterTest`; escaneo ZAP autenticado en `staging.yml` |

`issuer-uri` y `jwk-set-uri` se configuran por separado a propósito: dentro de Docker las claves se piden por la URL interna, mientras que el claim `iss` que viaja en el token es la externa. Unificarlos rompe la validación en contenedores.

Rutas públicas, y **solo** estas: Swagger UI, `/v3/api-docs`, `/v3/api-docs.yaml`, `/health` y los endpoints de actuator `health`, `info` y `prometheus`. El resto de actuator exige autenticación.

### RNF-02 — Autorización por permiso, no por rol

| | |
|---|---|
| **Criterio** | Ninguna decisión de acceso se toma por nombre de rol. Un usuario autenticado que pida un scope que su rol no permite **no** obtiene la capacidad. |
| **Origen** | *"cada operación crítica deberá verificar el permiso correspondiente"* |
| **Estado** | **Cumple** |
| **Implementación** | `@PreAuthorize("hasAuthority('SCOPE_…')")` en los controladores; intersección scope↔rol en [`SecurityConfig.java:140`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L140) |
| **Verificación** | `SecurityIntegrationTest`, `KeycloakJwtConverterTest`; [informe G-6](../testing/reportes/G-6-escalada-de-scopes.md) |

**Este control es el único que existe, y conviene entender por qué.** El escaneo exploratorio G-6 demostró en vivo que **Keycloak emite cualquier scope a cualquier usuario autenticado**: un `inv_viewer` obtuvo `product:manage`, `stock:manage`, `user:manage` y `audit:view` con solo pedirlos. El realm no comprueba el rol al emitir scopes opcionales.

La consecuencia es que la tabla `SCOPES_BY_ROLE` de Java no es una duplicación cómoda del IdP: es **el techo efectivo de permisos**. Lo que no aparezca en ella se descarta del token aunque Keycloak lo haya firmado. Corregirlo en la raíz (`scopeMappings` en el realm) es **G-8**; documentar la decisión es **ADR-002**. Hasta entonces, tocar esa tabla es tocar el control de acceso del sistema.

### RNF-03 — Refresh tokens

| | |
|---|---|
| **Criterio** | El flujo `grant_type=refresh_token` emite un token nuevo válido contra un Keycloak vivo, y un refresh inválido se rechaza con **400**. |
| **Origen** | Seguridad Obligatoria — *"Refresh tokens"* |
| **Estado** | **Cumple** |
| **Implementación** | Cliente público con PKCE; renovación en `frontend/src/lib/session.ts` |
| **Verificación** | 5 comprobaciones en el paso de API tests de `.github/workflows/staging.yml`, contra Keycloak desplegado; [informe SEC-2/S-2](../testing/reportes/SEC-2-S-2-ciclo-de-vida-del-token.md) |

### RNF-04 — Expiración de sesión

| | |
|---|---|
| **Criterio** | Al caducar el token, la sesión se renueva sola; si la renovación falla, la sesión **se cierra** en vez de quedar en un limbo con la interfaz pintada y toda petición en 401. |
| **Origen** | Seguridad Obligatoria — *"Expiración de sesiones"* |
| **Estado** | **Cumple** |
| **Implementación** | `onTokenExpired` y `onAuthRefreshError` conectados en `frontend/src/lib/session.ts` |
| **Verificación** | `session.test.ts` — 5 tests |

### RNF-05 — Policies

| | |
|---|---|
| **Criterio** | O se implementan Authorization Services de Keycloak (Resources, Policies, Permissions), o existe un ADR que argumente por qué roles + scopes cubren el modelo exigido. |
| **Origen** | *"modelo de seguridad completamente granular basado en: Keycloak, OAuth2, JWT, Roles, Permisos, Scopes y **Policies**"* |
| **Estado** | **Pendiente** |
| **Qué falta** | **G-1** en el plan: implementar (≈5 h) o justificar por ADR. "Policies" aparece nombrado literalmente en el enunciado, así que la opción de ignorarlo en silencio no existe |

### RNF-06 — Superficie HTTP endurecida

| | |
|---|---|
| **Criterio** | Sin estado de sesión en servidor; CORS restringido por entorno; los errores no filtran mensajes internos ni trazas. |
| **Origen** | Seguridad Obligatoria + Security Testing — *"Validación de CORS"* |
| **Estado** | **Cumple** |
| **Implementación** | `SessionCreationPolicy.STATELESS`; CORS en [`SecurityConfig.java:101`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java#L101) con orígenes por perfil; `server.error.include-message: never` e `include-stacktrace: never` |
| **Verificación** | `CorsProfilesTest`, `GlobalExceptionHandlerTest`; [informe P-2a](../testing/reportes/P-2a-perfil-demo.md) |

CSRF está deshabilitado, y es correcto: la autenticación viaja en la cabecera `Authorization`, no en cookie, así que no hay vector de envío automático de credenciales que proteger. `CorsProfilesTest` existe para que nadie "arregle" un problema de desarrollo local metiendo `localhost` en los perfiles `staging` o `prod`. Falta un test de CORS extremo a extremo por perfil (**TEST-11**).

### RNF-07 — Gestión de secretos

| | |
|---|---|
| **Criterio** | Cero credenciales en el código. Todo secreto entra por variable de entorno o por el gestor de secretos del CI. |
| **Origen** | Buenas Prácticas — *"Secrets management"*, *"No hardcoded credentials"* |
| **Estado** | **Parcial** |
| **Implementación** | Variables en `.env.example` y secretos de repositorio; corregido en BP-2 |
| **Qué falta** | **S-4b** (issue #47): `.github/workflows/staging.yml` sigue inyectando `JWT_SECRET` y `JWT_EXPIRATION_MS`, restos de un esquema de autenticación que ya no se usa. Es un secreto de repositorio **vivo que no protege nada**: coste 10 minutos, y mientras siga ahí el criterio no se cumple |

---

## 2. Rendimiento y capacidad

### RNF-08 — Tiempo de respuesta

| | |
|---|---|
| **Criterio** | **p(95) < 500 ms** en los endpoints de lectura bajo carga sostenida, medido con k6 contra el sistema desplegado. |
| **Origen** | Performance Testing — *"Tiempo de respuesta y Throughput"* · umbral **[criterio propio]** |
| **Estado** | **Pendiente** |
| **Implementación** | Existe la instrumentación para medirlo: `percentiles-histogram` activo y buckets SLO en `50ms, 100ms, 200ms, 500ms, 1s, 2s` (`application.yml:104-108`) |
| **Qué falta** | **T-3**: no hay ni una prueba de carga. El enunciado exige stress, load, usuarios concurrentes, tiempo de respuesta y throughput. Es la única de las ocho capas de testing que está literalmente a cero |

### RNF-09 — Concurrencia y consistencia del stock

| | |
|---|---|
| **Criterio** | Dos movimientos simultáneos sobre el mismo producto no dejan el stock inconsistente ni pierden una actualización. |
| **Origen** | **[criterio propio]** derivado de RF-09 |
| **Estado** | **Cumple** |
| **Implementación** | Pool Hikari de 10 conexiones (mín. 2 en reposo) con detección de fugas a los 60 s; `open-in-view: false`, que evita mantener la sesión JPA abierta durante el renderizado de la respuesta |
| **Verificación** | `StockServiceConcurrencyIT` |

### RNF-10 — Eficiencia de transporte y consulta

| | |
|---|---|
| **Criterio** | Ninguna respuesta de colección es ilimitada; las respuestas grandes viajan comprimidas; las escrituras masivas van por lotes. |
| **Origen** | **[criterio propio]** |
| **Estado** | **Parcial** |
| **Implementación** | Paginación obligatoria con `size = 20` por defecto (RF-04); compresión activa a partir de 1 KB (`application.yml:80`); `hibernate.jdbc.batch_size: 20` con `order_inserts` y `order_updates` |
| **Qué falta** | Está implementado pero **no medido**: sin T-3 no hay evidencia de que estos ajustes sirvan de algo bajo carga real |

---

## 3. Observabilidad

> *"El sistema debe implementar observabilidad completa"* — el área que más pesa después de Testing.

### RNF-11 — Los siete componentes obligatorios

| | |
|---|---|
| **Criterio** | Metrics (Prometheus), Traces (Tempo), Logs (Loki), Collector (Alloy), Dashboards (Grafana), Alerting (Alertmanager) e Instrumentación (OpenTelemetry), todos desplegados y **conectados entre sí**. |
| **Origen** | Observabilidad y Telemetría (Obligatorio) |
| **Estado** | **Cumple** — 7 de 7 |
| **Implementación** | `docker-compose.yml` y `observability/`; exportador OTLP hacia Alloy (`application.yml:113`) con muestreo al 100 % |
| **Verificación** | Verificado en vivo, componente a componente, en los informes de la Ola 2 |

Cinco de los siete no existían al empezar. Loki ingiere los **14 contenedores** restantes del stack, no solo el backend.

### RNF-12 — Métricas exigidas

| | |
|---|---|
| **Criterio** | CPU, Memoria, JVM, Latencia, Throughput, Error rate y Database pool, las siete con serie viva en Prometheus. |
| **Origen** | Requisitos Obligatorios de Observabilidad |
| **Estado** | **Cumple** — 7 de 7, con 5 targets activos |
| **Implementación** | Actuator + Micrometer; `node-exporter` y `postgres-exporter` añadidos en OBS-3 para CPU y memoria de host |
| **Verificación** | [informe de dashboards](../testing/reportes/OBS-dashboards.md) |

Se añaden **4 series de negocio** **[criterio propio]** — movimientos, unidades, alertas y productos bajo mínimo ([informe OBS-2/E-3](../testing/reportes/OBS-2-E-3-metricas-de-negocio.md)).

### RNF-13 — Campos obligatorios en los logs

| | |
|---|---|
| **Criterio** | Cada línea lleva `traceId`, `spanId`, `correlationId`, nivel, usuario y endpoint, y Loki puede filtrar por ellos. |
| **Origen** | Requisitos Obligatorios de Observabilidad |
| **Estado** | **Cumple** — 6 de 6 |
| **Implementación** | `CorrelationIdFilter` envuelve la cadena entera (cubre también los 401); `AuthenticatedUserMdcFilter` añade el usuario tras validar el token |
| **Verificación** | `CorrelationIdFilterTest`, `AuthenticatedUserMdcFilterTest`; [informe OBS-4](../testing/reportes/OBS-4-logs-loki.md) |

**Solo en los perfiles `staging`, `prod` y `demo`**, que emiten JSON estructurado. En `dev` el log es texto plano y los campos no son filtrables.

### RNF-14 — Trazas distribuidas

| | |
|---|---|
| **Criterio** | Request tracing, database tracing, external calls y errores distribuidos. |
| **Origen** | Requisitos Obligatorios de Observabilidad |
| **Estado** | **Parcial** — 3 de 4 |
| **Implementación** | Bridge OTel + exportador OTLP + instrumentación JDBC: **12 spans por petición**, servicio `inventory-api` |
| **Qué falta** | Verificar el caso de **errores distribuidos**: que un fallo propague su traza a través de los servicios y sea consultable en Tempo |

### RNF-15 — Dashboards

| | |
|---|---|
| **Criterio** | Cuatro dashboards **separados**: Infraestructura, Aplicación, Negocio y Seguridad. |
| **Origen** | Requisitos Obligatorios de Observabilidad |
| **Estado** | **Cumple** — 4 de 4 |
| **Implementación** | `observability/grafana/provisioning/dashboards/01-infraestructura.json` … `04-seguridad.json`; datasources de Prometheus, Tempo y Loki provisionados, con correlación traza↔log en ambos sentidos |
| **Verificación** | 37 consultas revisadas panel por panel, ninguno vacío; 6 capturas con datos reales ([informe P-2](../testing/reportes/P-2-capturas-de-evidencia.md)) |

### RNF-16 — Alertas

| | |
|---|---|
| **Criterio** | Alto consumo de CPU, error rate elevado, latencia alta, servicios caídos y fallos de autenticación, las cinco enrutadas a Alertmanager. |
| **Origen** | Requisitos Obligatorios de Observabilidad |
| **Estado** | **Cumple** — 5 de 5, más una de negocio |
| **Implementación** | `observability/prometheus/rules/alerts.yml` |
| **Verificación** | Dos verificadas disparando de extremo a extremo ([informe OBS-5](../testing/reportes/OBS-5-alertas.md)) |

"Fallos de autenticación" se cubre con los **401 del backend**, no con métricas de login de Keycloak: Keycloak 24 no expone series de login, solo métricas de Quarkus. Queda anotado que un 401 no detecta fuerza bruta contra el formulario del propio Keycloak, aunque el realm tenga `bruteForceProtected: true`; los eventos `LOGIN_ERROR` sí llegan a Loki y se ven en el dashboard de Seguridad.

---

## 4. Calidad, CI/CD y entornos

### RNF-17 — Cobertura de pruebas

| | |
|---|---|
| **Criterio** | **≥ 80 %** de líneas y de ramas en el backend, comprobado en CI, no en local. |
| **Origen** | Calidad de Código — *"Debe medirse: Coverage…"* · umbral **[criterio propio]** |
| **Estado** | **Cumple** |
| **Medición** | **84,5 %** de ramas y **92,1 %** de líneas (JaCoCo en Actions) |
| **Verificación** | Quality gate en `ci.yml`; `scripts/verificar-badges-cobertura.sh` falla si los badges se desfasan de la medición |

El frontend se mide aparte y está en **9,2 %** de líneas. El informe daba 100 % hasta que se configuró `coverage.include` en vitest: solo contaba las 14 sentencias que los tests importaban. Es el hueco de calidad conocido y declarado.

### RNF-18 — Análisis estático continuo

| | |
|---|---|
| **Criterio** | SonarCloud analiza **cada ejecución de CI** y publica Coverage, Bugs, Vulnerabilities, Code smells y Duplicación. |
| **Origen** | Calidad de Código (Obligatorio) |
| **Estado** | **Cumple** |
| **Medición** | 0 bugs, 0 vulnerabilidades, 0 % de duplicación, **0 code smells** tras resolver los 16 heredados |
| **Verificación** | 6 badges servidos por SonarCloud en el README; [informe Q-5](../testing/reportes/Q-5-code-smells.md) |

Spotless corre en fase `validate`, así que el formato se comprueba en cada `compile`, `test`, `verify` y `package`. Estaba declarado en el POM y desactivado con `-Dspotless.check.skip=true` en los ocho sitios donde se invocaba Maven (Q-2).

### RNF-19 — Pipeline de diez etapas, en dos motores

| | |
|---|---|
| **Criterio** | Checkout, Build, Unit tests, Integration tests, API tests, E2E tests, Security scan, Quality gates, Docker build y Deployment, en GitHub Actions **y** en Jenkins. |
| **Origen** | DevSecOps y CI/CD — Pipeline Obligatorio |
| **Estado** | **Parcial** |
| **Implementación** | GitHub Actions cubre **9 de 10**; Jenkins tiene **11 etapas escritas** y configuración como código en `docker/jenkins/` |
| **Qué falta** | Actions: falta **E2E** (**C-1**). Jenkins: solo las **4 primeras etapas** se han llegado a ejecutar. `Integration Tests` no arranca sobre Docker Desktop en Windows — el proxy del socket responde `400` incluso con el socket montado dentro del contenedor — y bloquea todo lo que va detrás. En los runners Linux de Actions esos mismos IT pasan en cada PR (issue #49): hace falta un agente Linux (**C-4**) |

### RNF-20 — Las ocho capas de testing

| | |
|---|---|
| **Criterio** | Unit, Integration, API/Contract, E2E, Security, Performance, Data y Exploratory, todas con evidencia. |
| **Origen** | Full Stack Testing (Obligatorio) |
| **Estado** | **Parcial** — 2 capas completas, 5 parciales, 1 a cero |

| Capa | Estado |
|---|---|
| 1. Unit | **Cumple** — 307 `@Test` en 33 ficheros |
| 2. Integration | Parcial — Testcontainers con base real sí; **Keycloak no** (**TEST-1**, obligatorio literal) |
| 3. API / Contract | Parcial — Postman sin CI (TEST-3), RestAssured sin uso (TEST-2) |
| 4. E2E | Parcial — 3 specs de Playwright escritos, **el pipeline no los ejecuta** (C-1, TEST-7/8/9) |
| 5. Security | Parcial — ZAP autenticado y sembrado con el OpenAPI, con umbral; faltan Dependency Check/Snyk (T-5) y CORS (TEST-11) |
| 6. Performance | **Cero** — T-3 |
| 7. Data | Parcial — migraciones y seeds sí; duplicados y constraints por cubrir (DATA-1/2) |
| 8. Exploratory | **Cumple** — 3 charters y 15 bugs como issues, con reproducción |

### RNF-21 — Tres entornos y pruebas contra el sistema desplegado

| | |
|---|---|
| **Criterio** | Development, Staging y Production. En staging deben ejecutarse integration, API, E2E y security tests **contra el sistema ya desplegado**, no durante el build. |
| **Origen** | Entornos Obligatorios |
| **Estado** | **Parcial** |
| **Implementación** | Seis perfiles: `dev`, `demo`, `staging`, `prod`, `test` y `smoke`. `staging.yml` despliega y después lanza API tests y el escaneo ZAP **contra el despliegue vivo**; `LiveDatabaseIT` corre contra la base desplegada y **falla si no la encuentra**, en vez de saltarse |
| **Qué falta** | De las cuatro familias que el enunciado exige sobre el entorno desplegado, faltan los **E2E** (C-1). Y **CI-2**: `production.yml` nunca se ha ejecutado, porque disparar un release `v1.0.0` es una decisión explícita, no un descuido |

El perfil `demo` existe porque la presentación necesita a la vez log JSON estructurado —sin él los paneles no filtran por usuario ni endpoint— y CORS hacia `localhost:3000`, y ningún perfil daba ambas cosas. Se descartó añadir `localhost` a `staging`, que declara espejar producción.

---

## 5. Datos, operación y repositorio

### RNF-22 — Migraciones versionadas

| | |
|---|---|
| **Criterio** | El esquema solo cambia por migración versionada. La aplicación **no** modifica el esquema al arrancar, y se niega a arrancar si el esquema no coincide. |
| **Origen** | Arquitectura Técnica Obligatoria — *"Migraciones: Flyway o Liquibase"* |
| **Estado** | **Cumple** |
| **Implementación** | Flyway con 7 migraciones (`V1` … `V7`), `validate-on-migrate: true`, `out-of-order: false`, `baseline-on-migrate: false` y JPA con `ddl-auto: validate` |
| **Verificación** | `ProductRepositoryIT` y `AuditIntegrationIT` levantan el esquema real con Testcontainers |

`ddl-auto: validate` es deliberado: con `update`, Hibernate parchearía el esquema por su cuenta y las migraciones dejarían de ser la única verdad.

### RNF-23 — Despliegue reproducible

| | |
|---|---|
| **Criterio** | El stack completo se levanta con un solo comando y sin editar ficheros a mano. |
| **Origen** | Arquitectura Técnica Obligatoria — *"Contenedores: Docker, Docker Compose"* |
| **Estado** | **Cumple** |
| **Implementación** | `docker-compose.yml` con **14 servicios**: base de datos, Keycloak y su base, backend, frontend y los nueve de observabilidad (Redis se retiró en INF-1, estaba desplegado sin uso) |
| **Verificación** | Levantado desde cero para las capturas de P-2 |

Riesgo abierto: `keycloak-init` **no es idempotente**. Al reejecutarse sobre un realm existente lanza `duplicate key … uk_cli_scope` y ensucia el panel de eventos (**P-2b**, issue #45). Afecta directamente al ensayo de la presentación, que es un `down -v && up` repetido.

### RNF-24 — Repositorio y trabajo colaborativo

| | |
|---|---|
| **Criterio** | Repositorio público, README profesional, issues, pull requests, estrategia de ramas, Conventional Commits, branch protection y participación equitativa de ambos integrantes. |
| **Origen** | Repositorio GitHub + Buenas Prácticas Obligatorias |
| **Estado** | **Parcial** |
| **Implementación** | **31 issues** (13 épicas, 15 bugs, 3 charters) y **33 PRs**; `main` protegida con 4 checks, 2 obligatorios; commitlint activo; README con las rutas reales, la matriz rol→scopes y el aviso del `scope` obligatorio |
| **Qué falta** | **Code Reviews.** El enunciado los evalúa y BP-1 contó 6 de 10 PRs sin revisión. Ambos integrantes tienen permiso de escritura, así que la aprobación cuenta y sale gratis: es el punto más barato de todo el proyecto y sigue abierto |
