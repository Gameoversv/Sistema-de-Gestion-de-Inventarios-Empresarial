# Sistema de Gestión de Inventarios Empresarial

![CI](https://img.shields.io/github/actions/workflow/status/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial/ci.yml?label=CI&style=flat-square)
![Coverage](https://img.shields.io/badge/coverage-placeholder-brightgreen?style=flat-square)
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
| Frontend | React 18 + TypeScript |
| Base de Datos | PostgreSQL 16 |
| Cache | Redis 7 |
| Mensajería | RabbitMQ / Kafka (TBD — ver [ADR-001](docs/decisions/ADR-001-stack-selection.md)) |
| Contenedores | Docker + Docker Compose |
| Observabilidad | Prometheus + Grafana + Loki |
| CI/CD | GitHub Actions |
| Testing | JUnit 5, Testcontainers, Playwright, k6 |

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
