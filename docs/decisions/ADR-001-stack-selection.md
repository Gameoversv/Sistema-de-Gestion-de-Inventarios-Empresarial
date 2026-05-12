# ADR-001 — Elección de Stack Tecnológico

- **Estado:** Aceptado
- **Fecha:** 2026-05-12
- **Autores:** Equipo de desarrollo
- **Revisores:** Docente

---

## Contexto

El proyecto requiere construir un sistema de gestión de inventarios empresarial que incluya:

- API REST robusta con autenticación y autorización
- Interfaz de usuario moderna y reactiva
- Pipeline de CI/CD con testing automatizado en múltiples capas
- Observabilidad: métricas, logs y trazas
- Contenedorización y despliegue reproducible

El contexto académico también exige que el stack sea ampliamente documentado, con abundante material de aprendizaje, y que cubra prácticas de DevSecOps.

---

## Decisión

Se adoptará el siguiente stack:

### Backend
**Java 21 + Spring Boot 3**

### Frontend
**React 18 + TypeScript + Vite**

### Base de Datos
**PostgreSQL 16**

### Cache
**Redis 7**

### Contenedores
**Docker + Docker Compose**

### Observabilidad
**Prometheus + Grafana + Loki**

### CI/CD
**GitHub Actions**

### Testing
- Backend: JUnit 5, Mockito, Testcontainers, REST Assured
- Frontend: Vitest, React Testing Library, Playwright
- Performance: k6

---

## Alternativas Consideradas

### Backend

| Alternativa | Razón de descarte |
|-------------|------------------|
| Node.js + NestJS | Menor madurez en ecosistema empresarial Java-heavy; el equipo tiene mayor familiaridad con Java |
| Python + FastAPI | Excelente para microservicios de IA, menos adecuado para aplicaciones CRUD empresariales con ORM robusto |
| Quarkus | Menor adopción en entornos académicos; curva de aprendizaje adicional sin beneficio claro en este contexto |

### Frontend

| Alternativa | Razón de descarte |
|-------------|------------------|
| Angular | Mayor curva de aprendizaje; overhead para el alcance del proyecto |
| Vue.js 3 | Ecosistema más pequeño; menos demanda en mercado laboral local |

### Base de Datos

| Alternativa | Razón de descarte |
|-------------|------------------|
| MySQL 8 | PostgreSQL ofrece mejor soporte de tipos avanzados (JSONB, arrays), mejor concurrencia y cumplimiento ACID |
| MongoDB | Modelo de datos de inventario es inherentemente relacional; NoSQL agregaría complejidad innecesaria |

---

## Consecuencias

### Positivas

- Spring Boot 3 ofrece ecosistema maduro: Spring Security, Spring Data JPA, Actuator, Micrometer
- Java 21 incluye Virtual Threads (Loom), mejorando rendimiento sin cambios de arquitectura
- React + TypeScript es el stack frontend más demandado; maximiza valor del aprendizaje
- PostgreSQL es battle-tested para aplicaciones empresariales
- GitHub Actions tiene integración nativa con el repositorio, sin costo adicional
- Prometheus + Grafana es el estándar de facto para observabilidad en contenedores

### Negativas / Riesgos

- Java tiene mayor verbosidad que alternativas modernas → mitigado con Lombok y records de Java 21
- Spring Boot consume más RAM en contenedores que alternativas nativas → mitigado con profiles de dev optimizados
- Curva de aprendizaje de Testcontainers para integraciones → se proveerá documentación en `docs/`

---

## Referencias

- [Spring Boot 3 Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [React 18 Documentation](https://react.dev/)
- [PostgreSQL 16 Release Notes](https://www.postgresql.org/docs/16/release-16.html)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [ADR Template — Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
