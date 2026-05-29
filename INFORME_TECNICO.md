# Informe Técnico Completo — Sistema de Gestión de Inventarios Empresarial

**Proyecto:** Sistema de Gestión de Inventarios Empresarial  
**Institución:** PUCMM — Aseguramiento de Calidad del Software  
**Docente:** Freddy Peña  
**Fecha:** 2026-05-29  
**Rama activa:** `develop`  
**Total de commits:** 22  
**Total de tests:** 81 (+ 2 de concurrencia con Testcontainers)

> Este documento es privado y no se sube al repositorio (excluido en `.gitignore`).  
> Cubre **absolutamente todo** lo implementado desde el commit inicial hasta el estado actual.

---

## Índice

1. [Stack tecnológico](#1-stack-tecnológico)
2. [Fase 1 — Estructura del repositorio](#2-fase-1--estructura-del-repositorio)
3. [Fase 2 — Backend Spring Boot base (DDD)](#3-fase-2--backend-spring-boot-base-ddd)
4. [Fase 3 — Infraestructura: Docker, Keycloak, Logging](#4-fase-3--infraestructura-docker-keycloak-logging)
5. [Fase 4 — Seguridad JWT y endpoints de sesión](#5-fase-4--seguridad-jwt-y-endpoints-de-sesión)
6. [Fase 5 — CRUD de Productos](#6-fase-5--crud-de-productos)
7. [Fase 6 — CRUD de Categorías + RFC 7807 + Tests de servicio](#7-fase-6--crud-de-categorías--rfc-7807--tests-de-servicio)
8. [Fase 7 — Control de Stock: IN / OUT / ADJUSTMENT](#8-fase-7--control-de-stock-in--out--adjustment)
9. [Fase 8 — Auditoría Envers (AuditController)](#9-fase-8--auditoría-envers-auditcontroller)
10. [Fase 9 — Concurrencia y bloqueo pesimista](#10-fase-9--concurrencia-y-bloqueo-pesimista)
11. [Fase 10 — Tests de controladores + fix 403](#11-fase-10--tests-de-controladores--fix-403)
12. [Fase 11 — OpenAPI/Swagger UI completo + Endpoints de Reportes](#12-fase-11--openapiswagger-ui-completo--endpoints-de-reportes)
13. [Base de datos completa (todas las migraciones)](#13-base-de-datos-completa-todas-las-migraciones)
14. [Suite de pruebas completa](#14-suite-de-pruebas-completa)
15. [Infraestructura de calidad de código](#15-infraestructura-de-calidad-de-código)
16. [Observabilidad y DevOps](#16-observabilidad-y-devops)
17. [Historial de commits completo](#17-historial-de-commits-completo)

---

## 1. Stack tecnológico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Lenguaje | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Build | Maven | 3.x (wrapper incluido) |
| Base de datos | PostgreSQL | 16-alpine |
| ORM | Hibernate / Spring Data JPA | 6.x |
| Auditoría BD | Hibernate Envers | incluido en Hibernate |
| Migraciones | Flyway | incluido en Spring Boot |
| Caché | Redis | 7-alpine |
| Seguridad | Keycloak | 24.0 |
| OAuth2/JWT | Spring Security Resource Server | incluido |
| Mapeo de objetos | MapStruct | 1.6.3 |
| Generación de código | Lombok | incluido en Spring Boot |
| Documentación API | SpringDoc OpenAPI (Swagger UI) | 2.6.0 |
| Métricas | Micrometer + Prometheus | incluido |
| Dashboards | Grafana | latest |
| Contenedores | Docker + Docker Compose | — |
| Formato de código | Spotless + google-java-format | 1.22.0 |
| Cobertura | JaCoCo | 0.8.12 |
| Calidad estática | SonarQube / SonarCloud | 4.0.0.4121 |
| Commits | Commitlint + Husky | — |
| Tests unitarios | JUnit 5 + Mockito | incluido |
| Tests integración | Testcontainers | 1.20.6 |
| Tests API | Rest Assured | incluido |
| Tests seguridad | Spring Security Test | incluido |

---

## 2. Fase 1 — Estructura del repositorio

**Commits:** `02f0260` → `55e6928` → `2110aef`

### Qué se configuró

#### Repositorio base

- `LICENSE` (MIT)
- `.gitignore` con exclusiones para Java/Maven, Node, IDEs, Docker, OS, logs, `.env`
- `.gitattributes` — fuerza LF en scripts shell (necesario para que funcionen en Docker Linux desde Windows)

#### GitHub Community Files

```
.github/
├── ISSUE_TEMPLATE/
│   ├── bug_report.md        ← Template para reportar bugs
│   ├── feature_request.md   ← Template para solicitar funcionalidades
│   └── task.md              ← Template para tareas técnicas
└── PULL_REQUEST_TEMPLATE.md ← Template de PR con checklist de QAS
```

#### CONTRIBUTING.md

Guía de contribución que define:
- Flujo de ramas (`main`, `develop`, feature branches)
- Formato de commits (Conventional Commits)
- Proceso de Pull Request
- Estándares de código y pruebas

#### Conventional Commits con Husky + Commitlint

```
package.json              ← dependencias de husky y commitlint
commitlint.config.js      ← reglas: tipos permitidos, max 100 chars
.husky/
├── commit-msg            ← hook que corre commitlint en cada commit
└── pre-commit            ← hook que puede correr linting/tests
```

**Tipos de commit permitidos:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`

El límite de 100 caracteres en el mensaje de commit lo impone el hook automáticamente, bloqueando commits con mensajes más largos.

#### Docker Compose inicial

Definición de red `inventory-net` y servicio `postgres` con health check. Base que se fue ampliando en fases siguientes.

#### ADR-001 (Architecture Decision Record)

`docs/decisions/ADR-001-stack-selection.md` — documenta la elección de Spring Boot + PostgreSQL + Keycloak con sus justificaciones técnicas.

#### Observabilidad base

`observability/prometheus/prometheus.yml` — configuración inicial de Prometheus apuntando al backend.

---

## 3. Fase 2 — Backend Spring Boot base (DDD)

**Commit:** `45ea6d4` — *"initialize Spring Boot backend with DDD package structure"*

### Estructura de paquetes (Domain-Driven Design)

```
backend/src/main/java/com/inventory/
├── InventoryApplication.java          ← Entry point Spring Boot
├── audit/
│   ├── domain/
│   │   ├── AuditLog.java              ← Entidad de log manual (pre-Envers)
│   │   └── RevisionInfo.java          ← Entidad de revisión Envers
│   ├── repository/AuditLogRepository.java
│   ├── service/AuditService.java
│   └── package-info.java
├── common/
│   ├── config/JpaAuditingConfig.java  ← @EnableJpaAuditing
│   ├── domain/BaseEntity.java         ← id, createdAt, updatedAt
│   └── exception/
│       ├── BusinessException.java
│       ├── ConflictException.java
│       ├── GlobalExceptionHandler.java
│       └── ResourceNotFoundException.java
├── product/
│   ├── domain/
│   │   ├── Category.java
│   │   └── Product.java
│   ├── repository/
│   │   ├── CategoryRepository.java
│   │   └── ProductRepository.java
│   ├── service/
│   │   ├── ProductService.java
│   │   └── ProductServiceImpl.java    ← implementación base inicial
│   └── package-info.java
├── report/
│   ├── service/ReportService.java     ← placeholder para reportes futuros
│   └── package-info.java
├── security/
│   ├── domain/User.java               ← usuario local (reemplazado por AppUser en Fase 3)
│   ├── repository/UserRepository.java
│   └── package-info.java
└── stock/
    ├── domain/StockMovement.java
    ├── repository/StockMovementRepository.java
    ├── service/
    │   ├── StockService.java
    │   └── StockServiceImpl.java      ← implementación base inicial
    └── package-info.java
```

### BaseEntity

Superclase de todas las entidades del dominio. Usa auditoría automática de JPA:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

`@CreatedDate` y `@LastModifiedDate` se rellenan automáticamente por Spring Data, sin necesidad de código manual.

### Entidades del dominio

#### Category

```java
@Entity @Table(name = "categories") @Audited
public class Category extends BaseEntity {
    @NotBlank @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;
}
```

#### Product

```java
@Entity @Table(name = "products") @Audited
public class Product extends BaseEntity {
    @NotBlank @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @NotBlank @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull @DecimalMin("0.00")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @NotNull @Min(0) @Column(nullable = false)
    private Integer stock;

    @Min(0) @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock;        // agregado en V6

    @Column(nullable = false)
    private Boolean active;              // agregado en V6 (soft delete)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}
```

#### StockMovement

```java
@Entity @Table(name = "stock_movements") @Audited
public class StockMovement extends BaseEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;           // IN | OUT | ADJUSTMENT

    @NotNull @Min(0) @Column(nullable = false)
    private Integer quantity;

    @Column(name = "quantity_before")
    private Integer quantityBefore;      // agregado en V7

    @Column(name = "quantity_after")
    private Integer quantityAfter;       // agregado en V7

    @Column(length = 100)
    private String performedBy;          // agregado en V7

    @Column(length = 500)
    private String reason;

    @Column(name = "reference_id")
    private String referenceId;

    public enum MovementType { IN, OUT, ADJUSTMENT }
}
```

### application.yml base

Configuración centralizada con soporte a variables de entorno para todos los entornos:

- **Datasource + HikariCP:** pool de conexiones ajustado (max 10, min 2, keepalive, leak detection)
- **JPA:** `ddl-auto: validate` (Flyway es el único que toca el esquema), `open-in-view: false`
- **Envers:** sufijo `_aud`, `store_data_at_delete: true` (guarda el estado antes de borrar)
- **Flyway:** `validate-on-migrate: true`, `out-of-order: false`
- **Redis:** configurable por variables de entorno
- **Actuator:** expone `health`, `info`, `prometheus`, `metrics`, `loggers`
- **Servidor:** contexto `/api/v1`, compresión de respuestas habilitada

### Perfiles de Spring

| Perfil | Archivo | Uso |
|--------|---------|-----|
| `dev` | `application-dev.yml` | Desarrollo local |
| `staging` | `application-staging.yml` | Entorno de staging |
| `prod` | `application-prod.yml` | Producción |
| `test` | `application-test.yml` | Tests automáticos |
| `smoke` | `application-smoke.yml` | Smoke test en Docker Desktop Windows |

### Migrations iniciales (V1, V2)

**V1** — Esquema base:
- `users` (auth local, reemplazada por `app_users` en V3)
- `categories` (id, name, description, timestamps)
- `products` (id, sku, name, description, price, stock, category_id, timestamps + índices)

**V2** — Tablas de inventario:
- `stock_movements` (id, product_id, type, quantity, reason, reference_id, timestamps + índices)
- `audit_logs` (log manual de auditoría pre-Envers)

---

## 4. Fase 3 — Infraestructura: Docker, Keycloak, Logging

**Commits:** `d40a822`, `77ba3e3`, `7c4b65f`, `d497fd3`

### HikariCP y configuración avanzada de JPA

Se añadió al `application.yml`:
- `hikari.pool-name: InventoryHikariPool`
- `hibernate.jdbc.batch_size: 20` (inserts y updates en batch para reducir round-trips)
- `order_inserts: true` y `order_updates: true` (agrupa sentencias similares para batching eficiente)

### AppUser — Espejo de Keycloak en base de datos

**Archivo:** `security/domain/AppUser.java`  
**Migración:** V3

Keycloak es la fuente de verdad para la identidad. `AppUser` es una tabla local que guarda metadatos adicionales del usuario:

```java
@Entity @Table(name = "app_users") @Audited
public class AppUser extends BaseEntity {
    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private UUID keycloakId;   // UUID del usuario en Keycloak

    @Email @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    private Role role;         // ADMIN | MANAGER | VIEWER

    private boolean enabled;

    public enum Role { ADMIN, MANAGER, VIEWER }
}
```

Esta tabla permite controlar acceso a funcionalidades de la aplicación (rol de app) independientemente de los roles/scopes de Keycloak.

### Hibernate Envers — Configuración completa

**Migración:** V4

Envers necesita tablas `_aud` por cada entidad auditada y una tabla `revinfo` para metadatos de revisión:

```sql
CREATE TABLE revinfo (
    rev      SERIAL  PRIMARY KEY,
    revtstmp BIGINT  NOT NULL           -- timestamp en milisegundos epoch
);

CREATE TABLE categories_aud (
    id, rev (PK compuesta), revtype, ...todos los campos auditados
);
CREATE TABLE products_aud (...);
CREATE TABLE stock_movements_aud (...);
CREATE TABLE app_users_aud (...);
```

`revtype` es un SMALLINT: 0=ADD, 1=MOD, 2=DEL.

**Configuración en application.yml:**
```yaml
hibernate:
  envers:
    audit_table_suffix: _aud
    revision_field_name: rev
    revision_type_field_name: revtype
    store_data_at_delete: true    # guarda el último estado antes de borrar
```

### Seed data (V5)

5 categorías y 9 productos de ejemplo para desarrollo y demos:

```
Electronics: Laptop Pro 15", Wireless Mouse, USB-C Hub
Clothing: Cotton T-Shirt
Food & Beverage: Arabica Coffee, Green Tea
Tools & Hardware: Cordless Drill 18V
Office Supplies: A4 Paper Ream, Ballpoint Pens
```

Más un `AppUser` admin placeholder que se sincroniza desde Keycloak al primer login.

### Dockerfile multi-stage

```dockerfile
# Stage 1: Build con Maven
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime mínimo
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

El multi-stage reduce el tamaño de la imagen final (~180 MB vs ~450 MB del builder).

### JSON Logging con Logback

**Archivo:** `backend/src/main/resources/logback-spring.xml`

En producción los logs salen como JSON estructurado (compatible con Loki/Grafana):

```json
{"timestamp":"2026-05-25T12:00:00Z","level":"INFO","logger":"com.inventory.stock","message":"Movement registered","traceId":"abc123"}
```

En desarrollo, formato legible por humanos.

### Keycloak — Identity Provider

**Commit:** `d497fd3`

#### docker-compose.yml — Servicios de Keycloak

```yaml
keycloak-db:           # PostgreSQL exclusivo para Keycloak
  image: postgres:16-alpine

keycloak:              # Keycloak 24.0
  image: quay.io/keycloak/keycloak:24.0
  command: start-dev --import-realm    # importa realm-export.json al iniciar
  ports: "8180:8080"

keycloak-init:         # One-shot: crea scopes y usuarios de prueba
  image: alpine:3.19
  entrypoint: /init-users.sh
```

#### realm-export.json

Configuración completa del realm `inventory`:
- Nombre del realm: `inventory`
- Cliente: `inventory-backend` (tipo `confidential`, flujo `authorization_code` + `client_credentials`)
- Scopes OAuth2 personalizados creados en Keycloak

#### scripts/keycloak/init-users.sh

Script shell que usa la API REST de Keycloak para crear automáticamente:
- Los 7 scopes personalizados (`product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view`, `user:manage`, `audit:view`)
- Usuarios de prueba: `admin`, `manager`, `viewer`, `auditor` con sus scopes asignados

Esto elimina configuración manual de Keycloak al levantar el entorno.

### .gitattributes

```gitattributes
*.sh text eol=lf
*.sh eol=lf
```

Sin esto, Git en Windows convierte los scripts `.sh` a CRLF, que causa errores `\r: command not found` en Docker Linux.

---

## 5. Fase 4 — Seguridad JWT y endpoints de sesión

**Commits:** `cb1abac`, `6821832`

### SecurityConfig

**Archivo:** `backend/src/main/java/com/inventory/common/config/SecurityConfig.java`

Configuración completa de Spring Security como Resource Server OAuth2:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity        // habilita @PreAuthorize en métodos
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(disabled)
            .cors(corsConfigurationSource())
            .sessionManagement(STATELESS)          // sin sesión HTTP
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter())))
            .build();
    }
}
```

#### Conversión de JWT a authorities de Spring Security

Keycloak mete los scopes en el claim `scope` (espacio-separado) y los roles en `realm_access.roles`. El converter personalizado los traduce a `GrantedAuthority`:

```java
private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    // Roles de Keycloak → ROLE_<nombre>
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null) {
        ((List<String>) realmAccess.get("roles"))
            .stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
    }

    // Scopes OAuth2 → SCOPE_<scope>
    String scope = jwt.getClaimAsString("scope");
    if (scope != null) {
        for (String s : scope.split(" ")) {
            if (!s.isBlank())
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
    }

    return List.copyOf(authorities);
}
```

#### JwtDecoder con validador de issuer

Separa el `jwk-set-uri` (URL interna Docker para obtener claves públicas) del `issuer-uri` (URL externa que Keycloak embebe en el claim `iss` del token). En entornos contenerizados estos son diferentes:

```java
@Bean
public JwtDecoder jwtDecoder(
    @Value("${...jwk-set-uri}") String jwkSetUri,
    @Value("${...issuer-uri}") String issuerUri) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        new JwtTimestampValidator()));
    return decoder;
}
```

#### CORS por perfil

`app.cors.allowed-origins` se puede sobreescribir por perfil. En desarrollo: `http://localhost:3000`. En producción: el dominio real.

### Permisos granulares — modelo de scopes

El sistema NO usa roles simples para autorizar acciones. Cada operación crítica requiere un scope específico:

| Módulo | Scope | Operaciones |
|--------|-------|-------------|
| Productos | `product:view` | GET /products, GET /products/{id} |
| Productos | `product:manage` | POST, PUT, PATCH, DELETE /products |
| Stock | `stock:view` | GET /api/stock/movements, GET /api/stock/alerts |
| Stock | `stock:manage` | POST /api/stock/movements + todo lo de view |
| Auditoría | `audit:view` | GET /api/audit/stock-movements |
| Reportes | `report:view` | GET /api/reports/stock-summary, GET /api/reports/low-stock |
| Usuarios | `user:manage` | (preparado para gestión de usuarios) |

**Implementación en controladores:**
```java
@PreAuthorize("hasAuthority('SCOPE_stock:manage')")
public ResponseEntity<StockMovementResponse> register(...) {}

@PreAuthorize("hasAuthority('SCOPE_stock:view') or hasAuthority('SCOPE_stock:manage')")
public Page<StockMovementResponse> list(...) {}
```

### HealthController

**Ruta:** `GET /health` (pública, sin autenticación)

Endpoint simple para health checks de load balancers y Docker healthcheck:

```json
{"status": "UP"}
```

### MeController

**Ruta:** `GET /me` (requiere autenticación)

Introspección del token JWT actual. Útil durante desarrollo para verificar qué scopes y roles tiene el usuario autenticado:

```json
{
    "subject": "user-abc",
    "email": "user@example.com",
    "preferredUsername": "manager.juan",
    "roles": ["inventory-admin"],
    "scopes": ["product:view", "product:manage", "stock:manage"],
    "expiresAt": "2026-05-25T20:00:00Z"
}
```

### PingController

**Ruta:** `GET /ping` (sin autenticación)

Endpoint mínimo para smoke tests rápidos:
```json
{"pong": true}
```

### Tests de seguridad

#### KeycloakJwtConverterTest (6 tests)

Verifica que la conversión de JWT a authorities funciona correctamente:
- Scope `"product:view stock:manage"` → `[SCOPE_product:view, SCOPE_stock:manage]`
- Rol de Keycloak `"inventory-admin"` → `ROLE_inventory-admin`
- JWT sin claims → lista vacía (no falla)
- Claims nulos manejados correctamente

#### SecurityIntegrationTest (3 tests)

Tests de integración de la cadena de filtros de seguridad:
- Request sin token → 401
- Request con token válido → pasa al controlador
- Request con token válido pero scope insuficiente → 403

#### HealthControllerTest (2 tests)

- `GET /health` sin token → 200 (endpoint público)
- `GET /health` con token → 200

#### MeControllerTest (5 tests)

- Sin token → 401
- Con JWT y `sub` → respuesta con subject correcto
- Con `ROLE_manager` → aparece en `roles[]`, scopes vacío
- Con `SCOPE_openid` y `SCOPE_profile` → aparecen en `scopes[]`, roles vacío
- Con claim `email` y `preferred_username` → aparecen en respuesta

---

## 6. Fase 5 — CRUD de Productos

**Commits:** `fecae97`, `14d86d8`

### Migración V6 — Soft delete y stock mínimo

```sql
ALTER TABLE products
    ADD COLUMN minimum_stock INTEGER NOT NULL DEFAULT 0 CHECK (minimum_stock >= 0),
    ADD COLUMN active         BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_products_active ON products(active);

-- Envers también necesita estas columnas en la tabla de auditoría
ALTER TABLE products_aud
    ADD COLUMN minimum_stock INTEGER,
    ADD COLUMN active         BOOLEAN;
```

`active = FALSE` es el "soft delete" — el producto no desaparece de la BD sino que se marca como inactivo. Esto preserva el historial de movimientos y la integridad referencial.

### DTOs de Producto

Todos implementados como **records** inmutables (Java 16+):

```java
// Entrada — crear producto
public record ProductCreateRequest(
    @NotBlank @Size(max = 100) String sku,
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    @NotNull @Min(0) Integer stock,
    @Min(0) Integer minimumStock,   // default 0 si null
    Boolean active,                  // default true si null
    Long categoryId) {}

// Salida — respuesta al cliente
public record ProductResponse(
    Long id, String sku, String name, String description,
    BigDecimal price, Integer stock, Integer minimumStock,
    Boolean active, Long categoryId, String categoryName,
    Instant createdAt, Instant updatedAt) {}

// Actualización completa (PUT — todos los campos requeridos)
public record ProductUpdateRequest(
    @NotBlank String sku, @NotBlank String name,
    String description,
    @NotNull @DecimalMin("0.00") BigDecimal price,
    @NotNull @Min(0) Integer stock,
    @Min(0) Integer minimumStock,
    Boolean active, Long categoryId) {}

// Actualización parcial (PATCH — solo campos enviados)
public record ProductPatchRequest(
    @Size(max = 100) String sku, @Size(max = 255) String name,
    String description, @DecimalMin("0.00") BigDecimal price,
    @Min(0) Integer stock, @Min(0) Integer minimumStock,
    Boolean active, Long categoryId) {}
```

### ProductMapper (MapStruct)

MapStruct genera la implementación en tiempo de compilación (sin reflection en runtime):

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "categoryId",   source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);

    @Mapping(target = "category", ignore = true)  // se resuelve manualmente en servicio
    Product toEntity(ProductCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void patchEntity(ProductPatchRequest request, @MappingTarget Product product);

    void updateEntity(ProductUpdateRequest request, @MappingTarget Product product);
}
```

`patchEntity` usa `NullValuePropertyMappingStrategy.IGNORE` — solo actualiza los campos no-nulos del request. Esto implementa correctamente la semántica PATCH sin necesidad de código manual de merging.

### ProductSpecification (JPA Criteria API)

Permite construir queries dinámicas componiendo condiciones:

```java
public final class ProductSpecification {
    public static Specification<Product> nameContains(String fragment) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("name")), "%" + fragment.toLowerCase() + "%");
    }

    public static Specification<Product> skuContains(String fragment) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("sku")), "%" + fragment.toLowerCase() + "%");
    }

    public static Specification<Product> nameOrSkuContains(String q) {
        return Specification.where(nameContains(q)).or(skuContains(q));
    }

    public static Specification<Product> hasCategory(Long categoryId) { ... }
    public static Specification<Product> isActive(Boolean active) { ... }
}
```

### ProductServiceImpl — Lógica de negocio

```java
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    // CREATE — valida SKU único antes de guardar
    public ProductResponse create(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.sku()))
            throw new ConflictException("SKU already exists: " + request.sku());
        Product product = productMapper.toEntity(request);
        // defaults
        product.setMinimumStock(request.minimumStock() != null ? request.minimumStock() : 0);
        product.setActive(request.active() != null ? request.active() : Boolean.TRUE);
        if (request.categoryId() != null)
            product.setCategory(resolveCategory(request.categoryId()));
        return productMapper.toResponse(productRepository.save(product));
    }

    // READ — con Specification para filtros dinámicos
    public Page<ProductResponse> findAll(String search, Long categoryId, Boolean active, Pageable pageable) {
        Specification<Product> spec = Specification.where(null);
        if (search != null && !search.isBlank())
            spec = spec.and(ProductSpecification.nameOrSkuContains(search));
        if (categoryId != null)
            spec = spec.and(ProductSpecification.hasCategory(categoryId));
        if (active != null)
            spec = spec.and(ProductSpecification.isActive(active));
        return productRepository.findAll(spec, pageable).map(productMapper::toResponse);
    }

    // UPDATE (PUT) — reemplaza todos los campos, valida SKU si cambia
    public ProductResponse update(Long id, ProductUpdateRequest request) { ... }

    // PATCH — actualiza solo los campos enviados (no null)
    public ProductResponse patch(Long id, ProductPatchRequest request) { ... }

    // DELETE (soft) — marca active=false, no borra el registro
    public void delete(Long id) {
        product.setActive(Boolean.FALSE);
        productRepository.save(product);
    }
}
```

### ProductController — Endpoints REST

**Base URL:** `/products` (bajo `/api/v1` del context-path)

| Método | Ruta | Scope | Descripción |
|--------|------|-------|-------------|
| `GET` | `/products` | `product:view` o `product:manage` | Lista paginada con filtros |
| `GET` | `/products/{id}` | `product:view` o `product:manage` | Obtener por ID |
| `POST` | `/products` | `product:manage` | Crear nuevo |
| `PUT` | `/products/{id}` | `product:manage` | Reemplazar completo |
| `PATCH` | `/products/{id}` | `product:manage` | Actualización parcial |
| `DELETE` | `/products/{id}` | `product:manage` | Soft delete (active=false) |

**Paginación:** Spring Data Pageable — `?page=0&size=20&sort=name,asc`

**Filtros del GET /products:**
- `search` — busca en nombre y SKU (case-insensitive)
- `categoryId` — filtra por categoría
- `active` — filtra por estado (true/false)

**Swagger UI:** disponible en `/swagger-ui.html` (público, sin autenticación requerida)

---

## 7. Fase 6 — CRUD de Categorías + RFC 7807 + Tests de servicio

**Commit:** `131beab`

### DTOs de Categoría

```java
public record CategoryCreateRequest(
    @NotBlank @Size(max = 100) String name,
    String description) {}

public record CategoryUpdateRequest(
    @NotBlank @Size(max = 100) String name,
    String description) {}

public record CategoryResponse(
    Long id, String name, String description,
    Instant createdAt, Instant updatedAt) {}
```

### CategoryServiceImpl

```java
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.name()))
            throw new ConflictException("Category already exists: " + request.name());
        return mapper.toResponse(categoryRepository.save(mapper.toEntity(request)));
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
            .map(mapper::toResponse).toList();
    }

    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category existing = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (!existing.getName().equals(request.name()) && categoryRepository.existsByName(request.name()))
            throw new ConflictException("Category already exists: " + request.name());
        existing.setName(request.name());
        existing.setDescription(request.description());
        return mapper.toResponse(categoryRepository.save(existing));
    }

    public void delete(Long id) {
        if (!categoryRepository.existsById(id))
            throw new ResourceNotFoundException("Category not found: " + id);
        categoryRepository.deleteById(id);
    }
}
```

### CategoryController — Endpoints REST

**Base URL:** `/categories`

| Método | Ruta | Scope | Descripción |
|--------|------|-------|-------------|
| `GET` | `/categories` | `product:view` o `product:manage` | Listar todas |
| `GET` | `/categories/{id}` | `product:view` o `product:manage` | Obtener por ID |
| `POST` | `/categories` | `product:manage` | Crear |
| `PUT` | `/categories/{id}` | `product:manage` | Actualizar |
| `DELETE` | `/categories/{id}` | `product:manage` | Eliminar (hard delete) |

### GlobalExceptionHandler — RFC 7807 Problem Details

El estándar RFC 7807 define un formato estándar para respuestas de error en APIs REST. Todos los errores del sistema siguen este formato:

```json
{
  "type": "https://inventory.api/problems/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Product not found: 99",
  "instance": "/api/v1/products/99"
}
```

**Excepciones manejadas:**

| Excepción | Status | Tipo |
|-----------|--------|------|
| `ResourceNotFoundException` | 404 | `/not-found` |
| `ConflictException` | 409 | `/conflict` |
| `BusinessException` | 422 | `/business-error` |
| `MethodArgumentNotValidException` | 400 | `/validation-error` |
| `ConstraintViolationException` | 400 | `/validation-error` |
| `AccessDeniedException` | 403 | `/access-denied` *(añadido en Fase 10)* |
| `Exception` (catch-all) | 500 | `/internal-error` |

El handler de validación extrae todos los errores de campo del `BindingResult`:
```json
{
  "type": "https://inventory.api/problems/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Validation failed",
  "errors": {
    "sku": "must not be blank",
    "price": "must be greater than or equal to 0.00"
  }
}
```

### Tests de validación de DTOs

#### ProductCreateRequestValidationTest (12 tests)

Verifica las restricciones Jakarta Validation directamente, sin levantar Spring:

```java
@Test void sku_blank_failsValidation() { ... }
@Test void price_negative_failsValidation() { ... }
@Test void price_null_failsValidation() { ... }
@Test void stock_negative_failsValidation() { ... }
@Test void minimumStock_negative_failsValidation() { ... }
// etc.
```

#### CategoryCreateRequestValidationTest (6 tests)

- Nombre en blanco → falla
- Nombre demasiado largo (>100 chars) → falla
- Nombre válido → pasa
- Descripción null → pasa (campo opcional)

### ProductServiceTest (14 tests)

Tests unitarios con Mockito:

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductMapper productMapper;
    @InjectMocks ProductServiceImpl productService;
    ...
}
```

| Test | Verifica |
|------|---------|
| `create_validRequest_savesAndReturns` | Create guarda y mapea correctamente |
| `create_duplicateSku_throwsConflict` | ConflictException si SKU existe |
| `create_withCategory_resolvesCategory` | Categoría se carga y asigna |
| `create_withNullCategory_noLookup` | No consulta categoría si categoryId es null |
| `findById_existingId_returnsResponse` | findById funciona correctamente |
| `findById_missingId_throwsNotFound` | ResourceNotFoundException si no existe |
| `findAll_noFilters_returnsAll` | findAll sin filtros |
| `findAll_withSearch_appliesSpec` | findAll aplica Specification |
| `update_changedSku_checksUniqueness` | PUT valida SKU único al cambiar |
| `update_sameSku_noConflictCheck` | PUT no verifica si el SKU no cambia |
| `patch_nullFields_skipsUpdate` | PATCH no modifica campos null |
| `delete_existing_setsInactive` | DELETE hace soft delete (active=false) |
| `delete_missing_throwsNotFound` | ResourceNotFoundException si no existe |
| `create_defaultsApplied` | minimumStock=0 y active=true por defecto |

### Colección Postman

`docs/postman/inventory-api.postman_collection.json` — colección completa con:
- Variables de entorno configuradas (`{{baseUrl}}`, `{{token}}`)
- Request de login a Keycloak para obtener token
- Todos los endpoints de Productos y Categorías con ejemplos de body
- Tests automáticos de Postman (status codes, estructura de respuesta)

---

## 8. Fase 7 — Control de Stock: IN / OUT / ADJUSTMENT

**Commits:** `b4b9e9e`, `1554499`, `6e3c861`, `96e9cb7`

### Migración V7 — Snapshots y username en auditoría

```sql
-- Columnas de snapshot en movimientos
ALTER TABLE stock_movements
    ADD COLUMN quantity_before INTEGER,
    ADD COLUMN quantity_after  INTEGER,
    ADD COLUMN performed_by    VARCHAR(100);

-- Las mismas columnas en la tabla de auditoría Envers
ALTER TABLE stock_movements_aud
    ADD COLUMN quantity_before INTEGER,
    ADD COLUMN quantity_after  INTEGER,
    ADD COLUMN performed_by    VARCHAR(100);

-- Username en metadatos de revisión Envers
ALTER TABLE revinfo ADD COLUMN username VARCHAR(100);

-- Relajar restricción: ADJUSTMENT puede ajustar a 0
ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS stock_movements_quantity_check;
ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_quantity_check CHECK (quantity >= 0);
```

### RevisionInfo — Captura de username Envers

**Archivo:** `audit/domain/RevisionInfo.java`

```java
@RevisionEntity(EnversRevisionListener.class)   // apunta al listener personalizado
public class RevisionInfo {
    @RevisionNumber int id;
    @RevisionTimestamp long timestamp;

    @Setter
    @Column(length = 100)
    private String username;   // quién creó esta revisión
}
```

### EnversRevisionListener — Extracción del JWT

**Archivo:** `audit/listener/EnversRevisionListener.java`

**No es un bean de Spring** — Envers lo instancia por reflexión. Por eso no puede usar `@Autowired`. En su lugar lee el `SecurityContextHolder` de forma estática (es un ThreadLocal — el thread de la request lo tiene disponible):

```java
public class EnversRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        RevisionInfo info = (RevisionInfo) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String username = jwt.getClaimAsString("preferred_username");
            info.setUsername(
                (username != null && !username.isBlank()) ? username : jwtAuth.getName()
            );
        } else if (auth != null && auth.getName() != null) {
            info.setUsername(auth.getName());
        }
        // Si no hay autenticación (contexto vacío), username queda null
    }
}
```

**Prioridad de claims:**
1. `preferred_username` (nombre legible en Keycloak, ej: "manager.juan")
2. Fallback: `sub` (UUID del usuario en Keycloak)
3. Fallback: `auth.getName()` (para autenticaciones no-JWT)

### DTOs de StockMovement

```java
// Entrada
public record StockMovementRequest(
    @NotNull Long productId,
    @NotNull MovementType type,       // IN | OUT | ADJUSTMENT
    @NotNull @Min(0) Integer quantity,
    @Size(max = 500) String reason,
    String referenceId) {}

// Salida — incluye datos desnormalizados del producto para evitar llamadas extra
public record StockMovementResponse(
    Long id,
    Long productId, String sku, String productName,
    MovementType type,
    Integer quantity, Integer quantityBefore, Integer quantityAfter,
    String reason, String referenceId, String performedBy,
    Instant createdAt) {}
```

### StockMovementMapper (MapStruct)

```java
@Mapper(componentModel = "spring")
public interface StockMovementMapper {
    @Mapping(target = "productId",   source = "product.id")
    @Mapping(target = "sku",         source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    StockMovementResponse toResponse(StockMovement movement);
}
```

### StockMovementRepository — Queries filtradas

Query JPQL con filtros opcionales usando el patrón `:param IS NULL OR`:

```java
@Query("""
    SELECT m FROM StockMovement m
    WHERE (:productId IS NULL OR m.product.id = :productId)
      AND (:type      IS NULL OR m.type = :type)
      AND (:from      IS NULL OR m.createdAt >= :from)
      AND (:to        IS NULL OR m.createdAt <= :to)
    ORDER BY m.createdAt DESC
    """)
Page<StockMovement> findFiltered(
    @Param("productId") Long productId,
    @Param("type")      MovementType type,
    @Param("from")      Instant from,
    @Param("to")        Instant to,
    Pageable pageable);
```

Este patrón evita construir queries dinámicas con Specification (que requeriría código más complejo y es más difícil de leer).

### ProductRepository — Nuevos métodos

```java
// Productos bajo stock mínimo — para alertas
@Query("SELECT p FROM Product p WHERE p.active = true AND p.stock <= p.minimumStock ORDER BY p.stock ASC")
List<Product> findLowStockProducts();

// Bloqueo pesimista — para operaciones de stock concurrentes
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);
```

### Eventos de dominio — Umbral de stock

#### StockThresholdCrossedEvent

```java
public record StockThresholdCrossedEvent(
    Long productId, String sku, String productName,
    int currentStock, int minimumStock) {}
```

#### StockThresholdListener

```java
@Component @Slf4j
public class StockThresholdListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThresholdCrossed(StockThresholdCrossedEvent event) {
        log.warn("STOCK ALERT — product={} sku={} currentStock={} minimumStock={}",
            event.productId(), event.sku(), event.currentStock(), event.minimumStock());
    }
}
```

`AFTER_COMMIT` garantiza que la alerta solo se procesa si la transacción confirmó exitosamente. Si hay rollback (ej: stock insuficiente), el evento se descarta automáticamente.

### StockService — Interface

```java
public interface StockService {
    StockMovementResponse registerMovement(StockMovementRequest request, Authentication authentication);
    int currentStock(Long productId);
    Page<StockMovementResponse> getMovements(Long productId, MovementType type,
                                              Instant from, Instant to, Pageable pageable);
    List<ProductResponse> getLowStockAlerts();
}
```

### StockServiceImpl — Lógica de negocio completa

#### registerMovement — Flujo completo

```
1. Extraer username del JWT (preferred_username o sub)
2. findByIdForUpdate(productId) → SELECT FOR UPDATE
   └─ Si no existe → ResourceNotFoundException (404)
3. quantityBefore = product.getStock()
4. Calcular newStock según tipo:
   ├─ IN:         newStock = current + quantity     [siempre válido si qty >= 0]
   ├─ OUT:        newStock = current - quantity     [BusinessException si result < 0]
   └─ ADJUSTMENT: newStock = quantity              [valor absoluto — ignora current]
5. product.setStock(newStock)
6. productRepository.save(product)
7. Crear StockMovement con todos los campos (incluyendo snapshots)
8. movementRepository.save(movement)
9. Si newStock <= minimumStock → publishEvent(StockThresholdCrossedEvent)
10. return movementMapper.toResponse(saved)
```

#### computeNewStock — Switch expression Java 21

```java
private int computeNewStock(MovementType type, int current, int quantity) {
    return switch (type) {
        case IN         -> current + quantity;
        case OUT        -> {
            if (current < quantity)
                throw new BusinessException(
                    "Stock insuficiente: disponible=%d, solicitado=%d"
                        .formatted(current, quantity));
            yield current - quantity;
        }
        case ADJUSTMENT -> quantity;
    };
}
```

`BusinessException` → GlobalExceptionHandler → 422 Unprocessable Entity con body RFC 7807.

### StockController — Endpoints REST

**Base URL:** `/api/stock`

| Método | Ruta | Scope | Descripción |
|--------|------|-------|-------------|
| `POST` | `/api/stock/movements` | `stock:manage` | Registrar movimiento |
| `GET` | `/api/stock/movements` | `stock:view` o `stock:manage` | Listar con filtros + paginación |
| `GET` | `/api/stock/alerts` | `stock:view` o `stock:manage` | Productos bajo stock mínimo |

**Ejemplo: POST /api/stock/movements**
```json
Request:
{
  "productId": 1,
  "type": "OUT",
  "quantity": 5,
  "reason": "Venta factura F-2026-001",
  "referenceId": "F-2026-001"
}

Response 201 Created:
{
  "id": 42,
  "productId": 1,
  "sku": "ELEC-001",
  "productName": "Laptop Pro 15\"",
  "type": "OUT",
  "quantity": 5,
  "quantityBefore": 50,
  "quantityAfter": 45,
  "reason": "Venta factura F-2026-001",
  "referenceId": "F-2026-001",
  "performedBy": "manager.juan",
  "createdAt": "2026-05-25T16:00:00Z"
}
```

---

## 9. Fase 8 — Auditoría Envers (AuditController)

**Commits:** `664a006`, `1b2f49c`

### AuditRevisionResponse

```java
public record AuditRevisionResponse(
    int revisionNumber,           // número secuencial de la revisión Envers
    Instant revisionTimestamp,    // cuándo ocurrió
    String revisedBy,             // username del @RevisionEntity
    RevisionType revisionType,    // ADD | MOD | DEL
    Long movementId,
    Long productId,
    String sku, String productName,
    MovementType movementType,
    Integer quantity, Integer quantityBefore, Integer quantityAfter,
    String performedBy,
    String reason) {}
```

### StockAuditService — Consultas AuditReader

**Archivo:** `audit/service/StockAuditService.java`

Usa la API de consultas de Hibernate Envers para consultar tablas `_aud` directamente:

```java
@Service @Transactional(readOnly = true)
public class StockAuditService {

    public List<AuditRevisionResponse> findMovementHistory(
            Long productId, String username, Instant from, Instant to) {

        AuditReader reader = AuditReaderFactory.get(entityManager);
        AuditQuery query = reader
            .createQuery()
            .forRevisionsOfEntity(StockMovement.class, false, true);
            // false → devuelve Object[] [entidad, RevisionInfo, RevisionType]
            // true  → incluye filas eliminadas

        // Filtros opcionales
        if (productId != null)
            query.add(AuditEntity.relatedId("product").eq(productId));

        if (username != null)
            query.add(AuditEntity.revisionProperty("username").eq(username));

        if (from != null)
            query.add(AuditEntity.revisionProperty("timestamp").ge(from.toEpochMilli()));

        if (to != null)
            query.add(AuditEntity.revisionProperty("timestamp").le(to.toEpochMilli()));

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
            .map(this::toResponse)
            .filter(Objects::nonNull)
            .toList();
    }

    private AuditRevisionResponse toResponse(Object[] row) {
        StockMovement movement = (StockMovement) row[0];
        RevisionInfo  revision = (RevisionInfo) row[1];
        RevisionType  revType  = (RevisionType) row[2];

        try {
            // Acceso a la asociación product (puede estar desconectada del contexto)
            Product product = movement.getProduct();
            return new AuditRevisionResponse(
                revision.getId(),
                Instant.ofEpochMilli(revision.getTimestamp()),
                revision.getUsername(),
                revType,
                movement.getId(),
                product != null ? product.getId() : null,
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                movement.getType(),
                movement.getQuantity(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getPerformedBy(),
                movement.getReason());
        } catch (LazyInitializationException ex) {
            // La asociación lazy no está disponible en contexto Envers
            log.warn("Cannot load product for movement id={}: {}", movement.getId(), ex.getMessage());
            return null;
        }
    }
}
```

**Corrección aplicada en commit `1b2f49c`:** El catch original era `catch (Exception ignored)` — swallaba silenciosamente cualquier error, incluyendo bugs reales. Se cambió a `catch (LazyInitializationException ex)` con un `log.warn` explícito. Esto deja pasar cualquier otra excepción correctamente.

### AuditController

**Archivo:** `audit/web/AuditController.java`

| Método | Ruta | Scope | Descripción |
|--------|------|-------|-------------|
| `GET` | `/api/audit/stock-movements` | `audit:view` | Historial de revisiones Envers |

El scope `audit:view` está deliberadamente separado de `stock:view` y `stock:manage`. Un gestor de stock puede operar con el inventario, pero ver la auditoría completa requiere permiso explícito de auditor.

**Filtros opcionales:**
- `productId` — historial de un producto específico
- `username` — movimientos realizados por un usuario específico
- `from` / `to` — rango de fechas (ISO-8601 Instant)

---

## 10. Fase 9 — Concurrencia y bloqueo pesimista

**Commit:** `583e13f`

### El problema de condiciones de carrera

Sin bloqueo, dos transacciones concurrentes sobre el mismo producto producen resultados incorrectos:

```
T1 lee: stock = 10
T2 lee: stock = 10       ← mismo valor que T1
T1 hace OUT(3): escribe 7, commit
T2 hace OUT(5): escribe 5, commit  ← ¡debería haber escrito 2!
```

El resultado correcto sería 2 (10 - 3 - 5), pero sin bloqueo es 5 (lost update).

### La solución: SELECT FOR UPDATE

`findByIdForUpdate()` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` genera en PostgreSQL:

```sql
SELECT * FROM products WHERE id = ? FOR UPDATE
```

La segunda transacción queda bloqueada hasta que la primera confirme o revierta:

```
T1: SELECT FOR UPDATE → bloquea fila
T2: SELECT FOR UPDATE → ESPERA (bloqueada por T1)
T1: escribe stock = 7, COMMIT
T2: obtiene lock → lee stock = 7, escribe 2, COMMIT
```

### StockServiceConcurrencyIT — Pruebas de concurrencia

**Archivo:** `test/java/com/inventory/stock/service/StockServiceConcurrencyIT.java`

Levanta PostgreSQL 16 real con Testcontainers. Redis se excluye del autoconfigure porque no hay Redis en el contexto de test:

```java
@SpringBootTest(webEnvironment = NONE,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
    })
@Testcontainers
@ActiveProfiles("test")
class StockServiceConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

#### Test 1: OUT concurrente — stock nunca negativo

```java
@Test
void concurrentOutRequests_stockNeverNegative() throws InterruptedException {
    // Producto con stock = 10
    // 10 threads, cada uno hace OUT(1) simultáneamente
    int threadCount = 10;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            ready.countDown();
            go.await();  // espera que todos estén listos
            try {
                stockService.registerMovement(new StockMovementRequest(productId, OUT, 1, null, null), auth);
                success.incrementAndGet();
            } catch (BusinessException e) { /* stock insuficiente */ }
        });
    }

    go.countDown();  // ¡todos a la vez!
    executor.awaitTermination(10, SECONDS);

    int finalStock = productRepository.findById(productId).get().getStock();
    assertThat(finalStock).isGreaterThanOrEqualTo(0);                  // nunca negativo
    assertThat(finalStock + success.get()).isEqualTo(10);              // no lost updates
}
```

#### Test 2: IN concurrente — todos los incrementos se reflejan

```java
@Test
void concurrentInRequests_stockIncreasesCorrectly() throws InterruptedException {
    // Producto con stock = 10
    // 5 threads, cada uno hace IN(2) simultáneamente
    // Resultado esperado: stock = 10 + (5 × 2) = 20
    ...
    assertThat(finalStock).isEqualTo(10 + success.get() * 2);
}
```

### Fix de Testcontainers en Windows

Docker Desktop 4.x en Windows rechaza la versión de API v1.24 que usa docker-java por defecto. Solución en `pom.xml`:

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <api.version>1.41</api.version>          <!-- Docker API version -->
        </systemPropertyVariables>
        <environmentVariables>
            <DOCKER_HOST>tcp://localhost:2375</DOCKER_HOST>
            <TESTCONTAINERS_HOST_OVERRIDE>localhost</TESTCONTAINERS_HOST_OVERRIDE>
        </environmentVariables>
    </configuration>
</plugin>
```

---

## 11. Fase 10 — Tests de controladores + fix 403

**Commit:** `51ab675`

### Fix: AccessDeniedException → 500 corregido a 403

**Bug:** El `@ExceptionHandler(Exception.class)` catch-all en `GlobalExceptionHandler` interceptaba `AccessDeniedException` (lanzada por `@PreAuthorize` cuando el scope es insuficiente) y devolvía 500 en lugar de 403.

**Causa raíz:** Con `@EnableMethodSecurity`, la verificación del scope ocurre dentro del método del controlador (a través de AOP), no en el filter chain. Cuando `@PreAuthorize` rechaza, lanza `AccessDeniedException` como excepción normal de Spring MVC, que llega al `@RestControllerAdvice` antes que al `ExceptionTranslationFilter`.

**Solución:**

```java
// En GlobalExceptionHandler:
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ProblemDetail> handleAccessDenied(
    AccessDeniedException ex, HttpServletRequest request) {
  ProblemDetail problem =
      ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
  problem.setType(URI.create(PROBLEM_BASE_URI + "/access-denied"));
  problem.setTitle("Access Denied");
  problem.setInstance(URI.create(request.getRequestURI()));
  return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
}
```

Spring resuelve handlers por especificidad: `AccessDeniedException.class` tiene prioridad sobre `Exception.class`.

### StockControllerTest (12 tests)

Usa `@WebMvcTest` — solo carga la capa web, sin JPA ni bases de datos:

```java
@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)    // necesario para que @PreAuthorize funcione
class StockControllerTest {
    @MockBean StockService stockService;
    @MockBean JwtDecoder jwtDecoder;   // sobreescribe el bean que intenta conectar a Keycloak

    // Simula un JWT con scope específico:
    .with(jwt()
        .jwt(j -> j.subject("manager"))
        .authorities(new SimpleGrantedAuthority("SCOPE_stock:manage")))
}
```

| Test | Status esperado | Qué verifica |
|------|----------------|--------------|
| `registerMovement_anonymous_returns401` | 401 | Sin token |
| `registerMovement_insufficientScope_returns403` | 403 | Con `stock:view` intentando POST |
| `registerMovement_validRequest_returns201WithBody` | 201 | Body completo mapeado correctamente |
| `registerMovement_nullProductId_returns400` | 400 | Validación `@NotNull productId` |
| `registerMovement_negativeQuantity_returns400` | 400 | Validación `@Min(0) quantity` |
| `listMovements_anonymous_returns401` | 401 | Sin token |
| `listMovements_withStockView_returns200` | 200 | `stock:view` puede listar |
| `listMovements_withStockManage_returns200` | 200 | `stock:manage` también puede listar |
| `listMovements_withTypeFilter_passesFilterToService` | 200 | Parámetro `type=IN` llega al servicio |
| `alerts_anonymous_returns401` | 401 | Sin token |
| `alerts_withStockView_returns200WithList` | 200 | Respuesta con productos bajo mínimo |
| `alerts_withAuditScope_returns403` | 403 | `audit:view` no tiene acceso a alerts |

### AuditControllerTest (8 tests)

| Test | Status esperado | Qué verifica |
|------|----------------|--------------|
| `movementHistory_anonymous_returns401` | 401 | Sin token |
| `movementHistory_withStockManageScope_returns403` | 403 | `stock:manage` no accede a auditoría |
| `movementHistory_withStockViewScope_returns403` | 403 | `stock:view` no accede a auditoría |
| `movementHistory_withAuditViewScope_returns200EmptyList` | 200 | `audit:view` funciona |
| `movementHistory_withAuditScope_returnsRevisionList` | 200 | Body con campos de revisión verificados |
| `movementHistory_withProductIdFilter_passesFilterToService` | 200 | `productId=99` llega al servicio |
| `movementHistory_withUsernameFilter_passesFilterToService` | 200 | `username=jdoe` llega al servicio |
| `movementHistory_withDateRangeFilter_passesFilterToService` | 200 | `from` y `to` como Instant al servicio |

---

## 12. Fase 11 — OpenAPI/Swagger UI completo + Endpoints de Reportes

**Commit:** pendiente

### Objetivos de la fase

- Documentación interactiva completa con Swagger UI
- Security scheme OAuth2/PKCE integrado en Swagger UI (permite autenticarse con Keycloak directamente desde la UI)
- Grupos temáticos de endpoints en Swagger UI
- `@ApiResponse` exhaustivo en todos los controladores
- `@Schema` y `@ExampleObject` en todos los DTOs y operaciones de escritura
- Capa de reportes del dashboard: `ReportController` + `ReportServiceImpl`
- Exportación de `openapi.yaml` al directorio `docs/api/` como artefacto del build

---

### OpenApiConfig — Configuración principal

**Archivo:** `common/config/OpenApiConfig.java`

```java
@Configuration
public class OpenApiConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String kcIssuerUri;

  @Bean
  public OpenAPI inventoryOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Inventory Management API")
            .version("1.0.0")
            .description("Sistema de Gestión de Inventarios Empresarial — PUCMM. ...")
            .contact(new Contact().name("PUCMM Inventory Team").email("..."))
            .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .addSecurityItem(new SecurityRequirement().addList("keycloak"))
        .components(new Components()
            .addSecuritySchemes("keycloak",
                new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .description("Keycloak OAuth2 Authorization Code + PKCE")
                    .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                            .authorizationUrl(kcIssuerUri + "/protocol/openid-connect/auth")
                            .tokenUrl(kcIssuerUri + "/protocol/openid-connect/token")
                            .scopes(new Scopes()
                                .addString("product:view", ...)
                                .addString("product:manage", ...)
                                .addString("stock:view", ...)
                                .addString("stock:manage", ...)
                                .addString("audit:view", ...)
                                .addString("report:view", ...))))));
  }
```

El `kcIssuerUri` se resuelve automáticamente desde la propiedad ya configurada en `application.yml`, evitando duplicación de configuración.

El `addSecurityItem(new SecurityRequirement().addList("keycloak"))` aplica el esquema de seguridad a **todas** las operaciones globalmente — sin necesidad de anotarlo individualmente en cada endpoint.

### Grupos de Swagger UI (GroupedOpenApi)

Cuatro grupos definen las secciones del dropdown de Swagger UI:

| Grupo | Display Name | Paths |
|-------|-------------|-------|
| `productos` | Productos y Categorías | `/products/**`, `/categories/**` |
| `stock` | Control de Stock | `/api/stock/**` |
| `reportes` | Reportes del Dashboard | `/api/reports/**` |
| `auditoria` | Auditoría Envers | `/api/audit/**` |

Los paths en `pathsToMatch` son las rutas del controller (sin context-path). SpringDoc los resuelve contra el mapping de Spring MVC por reflexión.

### Configuración en application.yml

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    oauth:
      client-id: ${KC_CLIENT_ID:inventory-frontend}
      use-pkce-with-authorization-code-grant: true   # PKCE habilitado
    try-it-out-enabled: true
    operations-sorter: alpha
    tags-sorter: alpha
    default-models-expand-depth: 1
  default-produces-media-type: application/json
  show-actuator: false
```

**URL de acceso:** `http://localhost:8080/api/v1/swagger-ui.html`

Spring Security ya permitía `/swagger-ui/**` y `/v3/api-docs/**` sin autenticación desde la Fase 4. No requirió cambios en `SecurityConfig`.

### @ApiResponse en controladores

Se adoptó un patrón de dos niveles para minimizar repetición:

**Nivel clase** — aplica a todos los métodos del controlador:
```java
@ApiResponse(responseCode = "401", description = "Token ausente o inválido",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "Scope insuficiente",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@SecurityRequirement(name = "keycloak")
public class ProductController { ... }
```

**Nivel método** — específico por operación:
```java
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Producto creado",
        content = @Content(schema = @Schema(implementation = ProductResponse.class),
            examples = @ExampleObject(name = "laptop", summary = "Ejemplo — laptop",
                value = "{ \"id\": 1, \"sku\": \"LAPTOP-001\", ... }"))),
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "409", description = "SKU ya existe",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
})
public ResponseEntity<ProductResponse> create(...) {}
```

Los errores usan `ProblemDetail.class` como schema — consistente con el RFC 7807 implementado en `GlobalExceptionHandler`.

Todos los controladores cubren los códigos: **200/201/204** (éxito), **400** (validación), **401** (no autenticado), **403** (scope insuficiente), **404** (no encontrado), **409** (conflicto).

### @Schema en DTOs

Anotaciones en todos los records de request y response:

```java
@Schema(description = "Datos para crear un nuevo producto")
public record ProductCreateRequest(
    @Schema(description = "Código único de producto", example = "LAPTOP-001")
        @NotBlank @Size(max = 100) String sku,
    @Schema(description = "Nombre del producto", example = "Laptop Dell XPS 15")
        @NotBlank @Size(max = 255) String name,
    @Schema(description = "Precio unitario", example = "1299.99")
        @NotNull @DecimalMin("0.00") BigDecimal price,
    ...
)
```

Las anotaciones conviven correctamente con las de Jakarta Validation — SpringDoc las lee independientemente.

### Capa de Reportes — Nuevos archivos

#### ReportService (interfaz actualizada)

```java
public interface ReportService {
  StockSummaryResponse stockSummary();
  LowStockReportResponse lowStockAlert(int threshold);
}
```

La interfaz original usaba `Map<String, Object>` (placeholder). Se reemplazó por DTOs tipados para poder documentar correctamente con `@Schema` y hacer la respuesta predecible para el frontend.

#### ReportServiceImpl

```java
@Service @RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

  private final ProductRepository productRepository;

  @Transactional(readOnly = true)
  public StockSummaryResponse stockSummary() {
    List<Product> all = productRepository.findAll();
    List<Product> active = all.stream().filter(p -> Boolean.TRUE.equals(p.getActive())).toList();

    BigDecimal totalValue = active.stream()
        .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getStock())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, List<Product>> grouped = active.stream()
        .collect(Collectors.groupingBy(
            p -> p.getCategory() != null ? p.getCategory().getName() : "Sin categoría"));

    List<CategoryStockDto> byCategory = grouped.entrySet().stream()
        .map(e -> toCategoryDto(e.getKey(), e.getValue()))
        .sorted(Comparator.comparing(CategoryStockDto::categoryName))
        .toList();

    return new StockSummaryResponse(all.size(), active.size(), lowStockCount, totalValue, byCategory);
  }

  @Transactional(readOnly = true)
  public LowStockReportResponse lowStockAlert(int threshold) {
    List<Product> lowStock = productRepository.findLowStockProducts();
    // threshold=0 → usa minimumStock por producto (ya filtrado por el query)
    // threshold>0 → filtra adicionalmente por stock <= threshold
    List<Product> filtered = threshold > 0
        ? lowStock.stream().filter(p -> p.getStock() <= threshold).toList()
        : lowStock;
    return new LowStockReportResponse(threshold, filtered.size(),
        filtered.stream().map(this::toLowStockItem).toList());
  }
}
```

Reutiliza `ProductRepository.findLowStockProducts()` ya existente (query JPQL con `active=true AND stock <= minimumStock`).

#### DTOs de Reportes

```java
@Schema(description = "Resumen de niveles de stock del inventario")
public record StockSummaryResponse(
    @Schema(example = "120") int totalProducts,
    @Schema(example = "115") int activeProducts,
    @Schema(example = "8")   int lowStockProducts,
    @Schema(example = "250000.00") BigDecimal totalInventoryValue,
    List<CategoryStockDto> byCategory) {}

@Schema(description = "Stock agrupado por categoría")
public record CategoryStockDto(
    @Schema(example = "Electrónica") String categoryName,
    @Schema(example = "15") long productCount,
    @Schema(example = "230") long totalStock,
    @Schema(example = "48500.00") BigDecimal totalValue) {}

@Schema(description = "Producto con stock por debajo del mínimo")
public record LowStockItemDto(
    Long id, String sku, String name,
    @Schema(example = "1") int currentStock,
    @Schema(example = "5") int minimumStock,
    @Schema(example = "4") int deficit,
    String categoryName) {}

@Schema(description = "Reporte de productos bajo stock mínimo")
public record LowStockReportResponse(
    @Schema(example = "5") int threshold,
    @Schema(example = "8") int totalItems,
    List<LowStockItemDto> items) {}
```

#### ReportController — Endpoints REST

**Base URL:** `/api/reports`

| Método | Ruta | Scope | Descripción |
|--------|------|-------|-------------|
| `GET` | `/api/reports/stock-summary` | `report:view` | Resumen del inventario agrupado por categoría |
| `GET` | `/api/reports/low-stock` | `report:view` | Productos bajo stock mínimo con filtro de umbral |

**Parámetros de `/api/reports/low-stock`:**
- `threshold` (default `0`) — si es 0, usa el `minimumStock` configurado por producto; si es > 0, filtra adicionalmente por `stock <= threshold`

### Exportación de openapi.yaml

**Profile Maven:** `generate-docs`

```xml
<profile>
  <id>generate-docs</id>
  <build>
    <plugins>
      <!-- spring-boot:start / spring-boot:stop en pre/post-integration-test -->
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution><id>pre-integration-test</id><goals><goal>start</goal></goals></execution>
          <execution><id>post-integration-test</id><goals><goal>stop</goal></goals></execution>
        </executions>
      </plugin>
      <!-- springdoc plugin llama /v3/api-docs.yaml y escribe el archivo -->
      <plugin>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-maven-plugin</artifactId>
        <version>2.0.0</version>
        <configuration>
          <apiDocsUrl>http://localhost:8080/api/v1/v3/api-docs.yaml</apiDocsUrl>
          <outputFileName>openapi.yaml</outputFileName>
          <outputDir>${project.basedir}/../docs/api</outputDir>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

**Uso:**
```bash
# Con Docker Compose levantado:
./mvnw verify -P generate-docs

# Resultado: docs/api/openapi.yaml
```

El plugin inicia la app Spring Boot, llama al endpoint `/v3/api-docs.yaml` (que SpringDoc genera por reflexión en tiempo de ejecución), y escribe el YAML en `docs/api/openapi.yaml`.

---

## 13. Base de datos completa (todas las migraciones)

| Versión | Descripción | Tablas afectadas |
|---------|-------------|-----------------|
| V1 | Esquema inicial | `users`, `categories`, `products` |
| V2 | Inventario y auditoría manual | `stock_movements`, `audit_logs` |
| V3 | Usuario espejo de Keycloak | `app_users` |
| V4 | Tablas de auditoría Envers | `revinfo`, `categories_aud`, `products_aud`, `stock_movements_aud`, `app_users_aud` |
| V5 | Datos de prueba | Inserts en `categories`, `products`, `app_users` |
| V6 | Soft delete y stock mínimo | `products` (+`minimum_stock`, `active`) + `products_aud` |
| V7 | Snapshots de stock y username | `stock_movements` (+3 cols), `stock_movements_aud` (+3 cols), `revinfo` (+`username`) |

### Diagrama de tablas principales

```
categories ──┐
             │ FK
products ────┤ (category_id)
             │
stock_movements (product_id FK)
             │
revinfo ─────┤ (rev FK)
             │
categories_aud, products_aud, stock_movements_aud, app_users_aud
```

---

## 14. Suite de pruebas completa

| Clase | Tipo | Tests | Framework |
|-------|------|-------|-----------|
| `StockServiceTest` | Unitario | 10 | JUnit 5 + Mockito |
| `StockControllerTest` | Web slice | 12 | JUnit 5 + WebMvcTest + Spring Security Test |
| `AuditControllerTest` | Web slice | 8 | JUnit 5 + WebMvcTest + Spring Security Test |
| `ProductServiceTest` | Unitario | 14 | JUnit 5 + Mockito |
| `HealthControllerTest` | Web slice | 2 | JUnit 5 + WebMvcTest |
| `MeControllerTest` | Web slice | 5 | JUnit 5 + WebMvcTest + Spring Security Test |
| `KeycloakJwtConverterTest` | Unitario | 6 | JUnit 5 |
| `SecurityIntegrationTest` | Integración web | 3 | JUnit 5 + SpringBootTest |
| `ProductCreateRequestValidationTest` | Validación | 12 | JUnit 5 + Validator |
| `CategoryCreateRequestValidationTest` | Validación | 6 | JUnit 5 + Validator |
| `InventoryApplicationTests` | Smoke | 1 | SpringBootTest |
| `StockServiceConcurrencyIT` | Concurrencia | 2 | JUnit 5 + Testcontainers + PostgreSQL |
| **TOTAL** | | **81** | |

---

## 15. Infraestructura de calidad de código

### JaCoCo — Cobertura de código

Configurado en `pom.xml` con umbral mínimo del 80%:

```xml
<limit>
    <counter>LINE</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.80</minimum>
</limit>
<limit>
    <counter>BRANCH</counter>
    <value>COVEREDRATIO</value>
    <minimum>0.80</minimum>
</limit>
```

**Exclusiones de la medición** (clases que no tiene sentido medir):
- `InventoryApplication.java` (solo el `main`)
- `config/**` (configuración de Spring, no lógica de negocio)
- `exception/**` (clases de excepción, solo constructores)
- `**/*Request.java`, `**/*Response.java` (records — solo datos)

El reporte HTML se genera en `target/site/jacoco/` y el XML en `target/site/jacoco/jacoco.xml` (consumido por SonarQube).

### SonarQube / SonarCloud

Plugin `sonar-maven-plugin` configurado. Se ejecuta con:

```bash
./mvnw sonar:sonar -Dsonar.host.url=http://localhost:9000
```

Variables sobreescribibles por CI:
```xml
<sonar.host.url>http://localhost:9000</sonar.host.url>
<sonar.coverage.jacoco.xmlReportPaths>target/site/jacoco/jacoco.xml</sonar.coverage.jacoco.xmlReportPaths>
```

### Spotless + google-java-format

Formateado automático del código Java en cada compilación:

```xml
<googleJavaFormat>
    <version>1.22.0</version>
    <style>GOOGLE</style>          <!-- 2 espacios de indentación -->
    <reflowLongStrings>true</reflowLongStrings>
</googleJavaFormat>
```

Se ejecuta en la fase `validate` (antes de compilar). Si el código no está formateado: `./mvnw spotless:apply` lo corrige automáticamente.

### Commitlint

```javascript
// commitlint.config.js
module.exports = {
    extends: ['@commitlint/config-conventional'],
    rules: {
        'type-enum': [2, 'always', ['feat','fix','refactor','docs','test','chore','perf','ci']],
        'header-max-length': [2, 'always', 100]
    }
};
```

Husky hook `.husky/commit-msg` ejecuta commitlint en cada commit, rechazando mensajes que no cumplan el formato.

### Rest Assured

Dependencia de test disponible para pruebas de API más expresivas:

```java
// Ejemplo de cómo se puede usar
given()
    .header("Authorization", "Bearer " + token)
    .contentType(JSON)
    .body(request)
.when()
    .post("/api/stock/movements")
.then()
    .statusCode(201)
    .body("sku", equalTo("ELEC-001"));
```

---

## 16. Observabilidad y DevOps

### Docker Compose completo

Servicios configurados con redes, health checks y dependencias:

```
inventory-net (red interna)
├── postgres:16-alpine        → BD principal (puerto 5432)
├── redis:7-alpine            → Caché con autenticación (puerto 6379)
├── keycloak-db               → BD exclusiva para Keycloak
├── keycloak:24.0             → Identity Provider (puerto 8180)
├── keycloak-init             → One-shot: crea scopes y usuarios
├── backend (Spring Boot)     → API REST (puerto 8080)
│   └── depends_on: postgres(healthy), redis(healthy), keycloak(healthy)
├── prometheus                → Métricas (puerto 9090)
│   └── depends_on: backend
└── grafana                   → Dashboards (puerto 3001)
    └── depends_on: prometheus
```

**Separación `issuer-uri` vs `jwk-set-uri`:**

El backend necesita dos URLs de Keycloak distintas:
- `KC_ISSUER_URI: http://localhost:8180/realms/inventory` — URL externa, embebida en el claim `iss` de los tokens
- `KC_JWK_SET_URI: http://keycloak:8080/realms/inventory/protocol/openid-connect/certs` — URL interna Docker para obtener claves públicas de firma

Sin esta separación, el backend no puede validar tokens en entornos contenerizados donde Keycloak no es accesible por `localhost` desde dentro del contenedor.

### Prometheus — Métricas

`observability/prometheus/prometheus.yml` — raspa el endpoint `/actuator/prometheus` del backend cada 15 segundos. Métricas disponibles:

- **JVM:** heap usage, GC, threads, class loading
- **HikariCP:** pool size, wait time, connection timeouts
- **HTTP:** `http_server_requests_seconds_count/sum` por ruta y status code
- **Custom (Micrometer):** configurables desde el código con `MeterRegistry`

### Grafana — Dashboards

Puerto 3001. Provisionamiento automático desde `observability/grafana/provisioning/`:
- `datasources/` — configura Prometheus como datasource automáticamente
- `dashboards/` — dashboards pre-configurados (pendientes de completar)

Grafana Admin password configurable por variable de entorno `GRAFANA_ADMIN_PASSWORD`.

### Variables de entorno (.env)

`.env.example` documenta todas las variables requeridas. `.env` está en `.gitignore` (nunca se sube al repositorio):

```bash
# Base de datos
DB_NAME=inventory_db
DB_USER=inventory_user
DB_PASSWORD=<secreto>

# Redis
REDIS_PASSWORD=<secreto>

# Keycloak
KC_DB_PASSWORD=<secreto>
KEYCLOAK_ADMIN_PASSWORD=<secreto>
KC_BACKEND_CLIENT_SECRET=<secreto>
KC_REALM=inventory

# App
JWT_SECRET=<secreto>
GRAFANA_ADMIN_PASSWORD=<secreto>
```

---

## 17. Historial de commits completo

| SHA | Tipo | Descripción | Fase |
|-----|------|-------------|------|
| `02f0260` | Initial | Initial commit — LICENSE + README | 1 |
| `2ce3853` | chore | .gitignore completo | 1 |
| `55e6928` | chore | Setup completo: estructura, GitHub templates, Husky, Docker base, ADR-001 | 1 |
| `2110aef` | docs | README actualizado con stack completo | 1 |
| `45ea6d4` | feat | Spring Boot backend — estructura DDD, entidades, repositorios, Flyway V1+V2 | 2 |
| `d40a822` | feat | HikariCP, AppUser, Envers (V3+V4), seed data V5 | 3 |
| `77ba3e3` | feat | JSON logging, SecurityConfig base, Dockerfile mejorado | 3 |
| `7c4b65f` | fix | Redis healthcheck, .gitattributes para scripts shell | 3 |
| `d497fd3` | feat | Keycloak: docker-compose, realm-export.json, init-users.sh | 3 |
| `cb1abac` | test | Tests de seguridad, smoke test fix para Docker Desktop Windows | 4 |
| `6821832` | feat | JWT scope mapping, CORS por perfil, /health y /me endpoints | 4 |
| `fecae97` | feat | DTOs de producto, MapStruct mapper, ProductSpecification, refactor servicio | 5 |
| `14d86d8` | feat | ProductController CRUD completo, paginación, filtros, soft delete, V6 | 5 |
| `131beab` | feat | CategoryController CRUD, RFC 7807 error responses, unit tests | 6 |
| `b4b9e9e` | feat | Migración V7: columnas snapshot + username en revinfo | 7 |
| `1554499` | feat | StockMovement snapshots, EnversRevisionListener, DTOs, mapper, repos, eventos | 7 |
| `6e3c861` | feat | StockServiceImpl IN/OUT/ADJUST, extracción JWT, eventos umbral, unit tests | 7 |
| `96e9cb7` | feat | StockController — endpoints REST con seguridad granular | 7 |
| `664a006` | feat | AuditController — GET /api/audit/stock-movements con filtros Envers | 8 |
| `1b2f49c` | fix | Reemplaza swallow genérico por LazyInitializationException específico | 8 |
| `583e13f` | test | Bloqueo pesimista (SELECT FOR UPDATE) + pruebas de concurrencia Testcontainers | 9 |
| `51ab675` | test | StockControllerTest (12) + AuditControllerTest (8) + fix AccessDeniedException → 403 | 10 |
| pendiente | feat | OpenAPI/Swagger UI completo: OpenApiConfig, grupos, OAuth2, @ApiResponse, @Schema, ReportController, generate-docs profile | 11 |

---

*Documento generado el 2026-05-29 para uso interno — PUCMM Aseguramiento de Calidad del Software.*
