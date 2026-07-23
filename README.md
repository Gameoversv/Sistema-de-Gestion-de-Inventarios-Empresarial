# Sistema de Gestión de Inventarios Empresarial

![CI](https://img.shields.io/github/actions/workflow/status/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/ci.yml?label=CI&style=flat-square)
![Backend coverage](https://img.shields.io/badge/backend%20coverage-92.2%25%20lineas-brightgreen?style=flat-square)
![Backend branches](https://img.shields.io/badge/backend%20branches-84.2%25-brightgreen?style=flat-square)
![Frontend coverage](https://img.shields.io/badge/frontend%20coverage-5.4%25-critical?style=flat-square)

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
├── backend/          # API REST Spring Boot
├── frontend/         # SPA React + TypeScript
├── observability/    # Prometheus, Grafana, Loki configs
├── docs/
│   └── decisions/    # Architecture Decision Records (ADRs)
├── scripts/          # Scripts de utilidad y automatización
├── docker-compose.yml
└── .env.example
```

## Inicio Rápido

```bash
# Clonar repositorio
git clone https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial.git
cd Sistema-de-Gestion-de-Inventarios-Empresarial

# Configurar variables de entorno
cp .env.example .env

# Levantar infraestructura
docker compose up -d

# Ver logs
docker compose logs -f
```

## Seguridad y Endpoints de API

Base URL: `http://localhost:8080/api/v1`

### Endpoints públicos (sin token)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/v1/health` | Health check de la aplicación |
| GET | `/api/v1/actuator/health` | Health de Spring Actuator (Docker probes) |

```bash
# Health check — sin autenticación
curl -s http://localhost:8080/api/v1/health | jq .
# { "status": "UP", "timestamp": "2024-01-15T10:30:00Z" }
```

### Endpoints protegidos (requieren JWT de Keycloak)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/v1/me` | Info del usuario autenticado (claims del JWT) |
| GET | `/api/v1/ping` | Ping con subject y authorities |

#### Obtener token de Keycloak

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/inventory/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=inventory-api" \
  -d "username=admin@example.com" \
  -d "password=changeme" \
  | jq -r '.access_token')
```

#### GET /api/v1/me

```bash
curl -s http://localhost:8080/api/v1/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Respuesta de ejemplo:
```json
{
  "subject": "a1b2c3d4-...",
  "email": "admin@example.com",
  "preferredUsername": "admin",
  "roles": ["inventory-admin"],
  "scopes": ["openid", "profile", "email"],
  "expiresAt": "2024-01-15T11:30:00Z"
}
```

#### GET /api/v1/ping

```bash
curl -s http://localhost:8080/api/v1/ping \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Respuesta de ejemplo:
```json
{
  "status": "ok",
  "subject": "admin@example.com",
  "authorities": "[ROLE_inventory-admin, SCOPE_openid, SCOPE_profile]"
}
```

#### Sin token — respuesta 401

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/me
# 401
```

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
- **OAuth2 scopes** (claim `scope`, separados por espacio) → prefijo `SCOPE_`
  - Ej: `"openid profile"` → `SCOPE_openid`, `SCOPE_profile`

Usar `@PreAuthorize` en controllers para autorización granular:
```java
@PreAuthorize("hasRole('inventory-admin')")
@PreAuthorize("hasAuthority('SCOPE_email')")
```

---

## Convención de Ramas

| Prefijo | Uso |
|---------|-----|
| `feature/` | Nuevas funcionalidades |
| `bugfix/` | Corrección de errores |
| `hotfix/` | Parches urgentes en producción |
| `chore/` | Tareas de mantenimiento / config |

## Documentación

- [ADR-001 — Elección de Stack](docs/decisions/ADR-001-stack-selection.md)
- [Guía de Contribución](CONTRIBUTING.md)

## Licencia

MIT — ver [LICENSE](LICENSE).
