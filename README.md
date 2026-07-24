# Sistema de Gestión de Inventarios Empresarial

![CI](https://img.shields.io/github/actions/workflow/status/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/ci.yml?label=CI&style=flat-square)
![Backend coverage](https://img.shields.io/badge/backend%20coverage-92.1%25%20lineas-brightgreen?style=flat-square)
![Backend branches](https://img.shields.io/badge/backend%20branches-84.4%25-brightgreen?style=flat-square)
![Frontend coverage](https://img.shields.io/badge/frontend%20coverage-7.5%25-critical?style=flat-square)

Las cinco métricas de calidad exigidas, medidas por SonarCloud en cada ejecución de CI:

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=coverage)](https://sonarcloud.io/component_measures?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=coverage)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=bugs)](https://sonarcloud.io/component_measures?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=bugs)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=vulnerabilities)](https://sonarcloud.io/component_measures?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=vulnerabilities)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=code_smells)](https://sonarcloud.io/component_measures?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=code_smells)
[![Duplicated Lines](https://sonarcloud.io/api/project_badges/measure?project=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=duplicated_lines_density)](https://sonarcloud.io/component_measures?id=Gameoversv_Sistema-de-Gestion-de-Inventarios-Empresarial&metric=duplicated_lines_density)

A diferencia de los tres de arriba, estos los sirve SonarCloud a partir del último análisis, así que no pueden quedarse desfasados.
![License](https://img.shields.io/github/license/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial?style=flat-square)
![Issues](https://img.shields.io/github/issues/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial?style=flat-square)
![Last Commit](https://img.shields.io/github/last-commit/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial?style=flat-square)

Sistema empresarial de gestión de inventarios con Full-Stack Testing, Observabilidad y DevSecOps integrado.

---

## Descripción

Plataforma para gestionar inventarios empresariales de forma segura y escalable. Incluye control de stock, movimientos, alertas, reportes y un pipeline completo de calidad con pruebas automatizadas, observabilidad y prácticas DevSecOps.

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Spring Boot 3 |
| Frontend | React 18 + TypeScript + Vite |
| Base de Datos | PostgreSQL 16 |
| Seguridad | Keycloak + OAuth2 + JWT |
| Contenedores | Docker + Docker Compose |
| Observabilidad | OpenTelemetry · Prometheus · Grafana · Tempo · Loki · Alloy · Alertmanager |
| CI/CD | GitHub Actions + Jenkins |
| Testing | JUnit 5 · Mockito · Testcontainers · RestAssured · Playwright · k6 · OWASP ZAP |

## Estructura del Proyecto

```
.
├── backend/              # API REST Spring Boot
├── frontend/             # SPA React + TypeScript
├── e2e/                  # Pruebas end-to-end con Playwright
├── keycloak/             # realm-export.json (realm, clientes, roles y scopes)
├── observability/
│   ├── prometheus/       # Scrape config y reglas de alerta
│   ├── alertmanager/     # Enrutado y agrupación de alertas
│   ├── grafana/          # Datasources y los 4 dashboards provisionados
│   ├── loki/             # Almacén de logs
│   ├── tempo/            # Almacén de trazas
│   └── alloy/            # Colector: OTLP a Tempo, logs a Loki
├── docs/
│   ├── api/              # OpenAPI generado
│   ├── decisions/        # Architecture Decision Records (ADRs)
│   ├── testing/          # Informes de QA y capturas de evidencia
│   ├── PLAN_EJECUCION.md
│   └── ANALISIS_BRECHAS.md
├── scripts/              # Inicialización de Keycloak, evidencia y verificaciones
├── docker/
│   └── jenkins/          # Imagen, plugins y configuración como código de Jenkins
├── Jenkinsfile
├── docker-compose.yml    # 15 servicios
└── .env.example
```

## Inicio Rápido

```bash
# Clonar repositorio
git clone https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial.git
cd Sistema-de-Gestion-de-Inventarios-Empresarial

# Configurar variables de entorno
cp .env.example .env

# Levantar infraestructura (15 servicios)
docker compose up -d

# Ver logs
docker compose logs -f
```

El arranque completo tarda un par de minutos: Keycloak importa el realm y el
backend espera a que esté sano antes de aceptar peticiones.

| Servicio | URL | Notas |
|---|---|---|
| Frontend | http://localhost:3000 | |
| API | http://localhost:8080 | Swagger UI en `/swagger-ui.html` |
| Keycloak | http://localhost:8180 | realm `inventory` |
| Grafana | http://localhost:3001 | 4 dashboards provisionados |
| Prometheus | http://localhost:9090 | |
| Alertmanager | http://localhost:9093 | |

### Elegir el perfil

`SPRING_PROFILES_ACTIVE` en `.env` cambia más de lo que parece:

| Perfil | Formato de log | Consecuencia |
|---|---|---|
| `dev` (por defecto) | texto legible | El panel de logs de Grafana **no puede filtrar por usuario ni endpoint** |
| `staging` / `prod` | JSON estructurado | Los 6 campos MDC llegan a Loki y son consultables |

Para una demo o cualquier trabajo sobre los dashboards de logs, usar `staging`.
Ojo: ese perfil fija el CORS a un dominio externo, así que el frontend local
queda bloqueado salvo que se añada `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000`
al `.env`.

## Seguridad y Endpoints de API

Base URL: `http://localhost:8080`

> Las rutas **no están versionadas** y no comparten un prefijo común: conviven
> `/products` con `/api/stock/...`. Es una inconsistencia conocida, registrada como
> **A-1** en el [plan de ejecución](docs/PLAN_EJECUCION.md); unificarlas cerca de la
> entrega se considera de riesgo alto. Los ejemplos de abajo usan las rutas reales.

### Endpoints públicos (sin token)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Health check de la aplicación |
| GET | `/actuator/health` | Health de Spring Actuator (probes de Docker) |
| GET | `/v3/api-docs` | Especificación OpenAPI |
| GET | `/swagger-ui.html` | Swagger UI |

```bash
curl -s http://localhost:8080/health | jq .
```

### Endpoints protegidos (requieren JWT de Keycloak)

| Método | Ruta | Scope necesario |
|--------|------|-----------------|
| GET | `/me` | autenticado |
| GET | `/ping` | autenticado |
| GET · POST | `/products` | `product:view` · `product:manage` |
| GET · POST | `/categories` | `product:view` · `product:manage` |
| POST | `/api/stock/movements` | `stock:manage` |
| GET | `/api/stock/movements` · `/api/stock/alerts` | `stock:view` |
| GET | `/api/reports/stock-summary` · `/low-stock` · `/critical-stock` · `/top-products` · `/dashboard-metrics` · `/recent-movements` | `report:view` |
| GET | `/api/audit/all` · `/products` · `/stock-movements` | `audit:view` |

#### Obtener token de Keycloak

El cliente es **`inventory-frontend`** (público, con *direct access grants*). El otro
cliente del realm, `inventory-backend`, es `bearerOnly`: solo valida tokens, no los
emite, y no tiene secreto.

Los usuarios de prueba los crea `scripts/keycloak/init-users.sh` al levantar el stack:
`inv_admin`, `inv_clerk`, `inv_auditor` e `inv_viewer`. Sus contraseñas salen del `.env`.

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/inventory/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=inventory-frontend" \
  -d "username=inv_admin" \
  -d "password=$KC_USER_ADMIN_PASSWORD" \
  --data-urlencode "scope=openid profile email product:view product:manage stock:view stock:manage report:view audit:view user:manage" \
  | jq -r '.access_token')
```

> **El parámetro `scope` no es opcional.** Keycloak solo incluye los scopes de negocio
> si se piden explícitamente. Sin él, el token llega con `scope: "profile email"` y
> **toda petición responde 403**, aunque el usuario sea administrador.
>
> Pedirlos todos es seguro: Keycloak los concede sin comprobar el rol, pero el backend
> los intersecta con el techo del rol antes de conceder autoridades. Un `inv_viewer`
> que pida `product:manage` lo recibe de Keycloak y aun así obtiene 403 al escribir.
> Ese recorte en Java es el control efectivo — ver
> [G-6](docs/testing/reportes/G-6-escalada-de-scopes.md).

#### Ejemplos con las rutas reales

```bash
# Quién soy, según el token
curl -s http://localhost:8080/me -H "Authorization: Bearer $TOKEN" | jq .

# Listado de productos
curl -s http://localhost:8080/products -H "Authorization: Bearer $TOKEN" | jq .

# Registrar un movimiento de stock
curl -s -X POST http://localhost:8080/api/stock/movements \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productId":1,"type":"IN","quantity":10,"reason":"Reposición"}' | jq .

# Productos por debajo del mínimo
curl -s http://localhost:8080/api/reports/low-stock \
  -H "Authorization: Bearer $TOKEN" | jq .
```

#### Los dos rechazos, que significan cosas distintas

```bash
# Sin token: 401 — no hay identidad
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/products

# Token válido sin el scope necesario: 403 — hay identidad, faltan permisos
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/products \
  -H "Authorization: Bearer $TOKEN_DE_VIEWER" -H 'Content-Type: application/json' \
  -d '{"sku":"X","name":"X","price":1,"stock":1,"minimumStock":0}'
```

El dashboard de Seguridad en Grafana los separa por ese motivo.

### CORS por perfil

| Perfil | Orígenes permitidos |
|--------|---------------------|
| `default` | `http://localhost:3000` |
| `dev` | `http://localhost:3000`, `http://localhost:5173`, `http://localhost:4200` |
| `staging` | `https://staging.inventory.example.com` |
| `prod` | `https://inventory.example.com` |

Configurar orígenes en el `application-{profile}.yml` correspondiente bajo `app.cors.allowed-origins`.

### Mapeo de autoridades JWT

El `JwtAuthenticationConverter` extrae dos tipos de autoridades del token:

- **Roles de Keycloak** (`realm_access.roles`) → prefijo `ROLE_`
  - Ej: `"inventory-admin"` → `ROLE_inventory-admin`
- **OAuth2 scopes** (claim `scope`, separados por espacio) → prefijo `SCOPE_`, **pero
  solo los permitidos para alguno de los roles del usuario**. El resto se descarta.

Esa intersección es el control de autorización real del sistema. Keycloak emite
cualquier scope a cualquier usuario autenticado, así que sin ella un `inv_viewer`
podría pedir `product:manage` y obtenerlo.

| Rol de realm | Scopes concedidos |
|---|---|
| `inventory-admin` | los siete |
| `warehouse-clerk` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
| `auditor` | `product:view`, `stock:view`, `report:view`, `audit:view` |
| `viewer` | `product:view`, `stock:view`, `report:view` |

La tabla vive en `SCOPES_BY_ROLE`, dentro de `SecurityConfig`. Si se toca ahí, hay que
actualizarla aquí: es la referencia que consulta cualquiera antes de llamar a la API.

Los controllers autorizan por scope, no por rol:
```java
@PreAuthorize("hasAuthority('SCOPE_product:manage')")
```

---

## Integración continua

| Workflow | Se dispara | Qué hace |
|---|---|---|
| `ci.yml` | PR hacia `main` y push a `main` | Tests unitarios, integración con Testcontainers, lint y cobertura del frontend, y análisis de SonarCloud |
| `staging.yml` | push a `main`/`develop`, o manual | Despliega el stack completo, ejecuta pruebas de API y de base desplegada, y un escaneo ZAP autenticado |
| `production.yml` | tags `v*.*.*` | Verifica y publica una GitHub Release |

Los cuatro checks de `ci.yml` son obligatorios y `main` exige revisión aprobatoria.

### Jenkins

El pipeline declarativo del `Jenkinsfile` se ejecuta en un Jenkins **configurado como
código**: plugins, credenciales, tool de JDK y el propio job viven en `docker/jenkins/`,
no dentro del volumen del contenedor.

```bash
export JENKINS_ADMIN_ID=admin
export JENKINS_ADMIN_PASSWORD=...
export JENKINS_ENV_FILE_B64=$(base64 -w0 .env)
docker compose -f docker/docker-compose.jenkins.yml up -d --build
```

Queda en http://localhost:8090 con el job `inventario-pipeline` ya creado. Acepta un
parámetro `BRANCH` para probar cambios del `Jenkinsfile` sin mezclarlos antes.

> La etapa de integración necesita **un agente Linux**: sobre Docker Desktop en Windows,
> Testcontainers no consigue hablar con el demonio. Está documentado en el propio
> `docker-compose.jenkins.yml` con las cuatro configuraciones probadas.

## Convención de Ramas

| Prefijo | Uso |
|---------|-----|
| `feat/` | Nuevas funcionalidades |
| `fix/` | Corrección de errores |
| `docs/` | Documentación y evidencia |
| `test/` | Pruebas |
| `ci/` · `chore/` | Pipelines y mantenimiento |

Los mensajes de commit siguen [Conventional Commits](https://www.conventionalcommits.org/),
verificado por commitlint en un hook de Husky.

## Documentación

- [Plan de ejecución](docs/PLAN_EJECUCION.md) — estado por área y trabajo pendiente
- [Análisis de brechas](docs/ANALISIS_BRECHAS.md)
- [Informes de QA](docs/testing/reportes/) — hallazgos con reproducción y evidencia
- [Capturas de observabilidad](docs/testing/capturas/)
- [ADR-001 — Elección de Stack](docs/decisions/ADR-001-stack-selection.md)
- [Guía de Contribución](CONTRIBUTING.md)

## Licencia

MIT — ver [LICENSE](LICENSE).
