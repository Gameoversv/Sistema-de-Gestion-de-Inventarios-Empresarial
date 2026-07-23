# OBS-4 — Logs con contexto y agregación en Loki

**Fecha:** 2026-07-22
**Entorno:** stack local en `feat/obs-4-loki-logs`, Loki 3.3.2, Alloy 1.5.1, Grafana latest, backend Spring Boot 3.3.5
**Estado:** verificado en vivo

---

## Resumen

Cierra las dos brechas restantes del bloque de observabilidad salvo los dashboards:

- **Loki** era el último de los siete componentes obligatorios que faltaba. Ya ingiere los logs de **los 14 contenedores**, no solo los del backend.
- **OBS-4** — los logs pasan de **1 campo de los 6 exigidos** (`nivel`) a **los 6**: `traceId`, `spanId`, `correlationId`, `nivel`, `usuario` y `endpoint`.

---

## Qué se implementó

| Pieza | Archivo |
|---|---|
| Loki monolítico, TSDB en disco, retención 7 días | `observability/loki/loki.yml` |
| Servicio `loki` + volumen `loki_data` | `docker-compose.yml` |
| Recolección de logs por socket de Docker | `observability/alloy/config.alloy` |
| Datasource de Loki con derived field `traceId` → Tempo | `observability/grafana/provisioning/datasources/loki.yml` |
| Correlación inversa Tempo → Loki (`tracesToLogsV2`) | `observability/grafana/provisioning/datasources/tempo.yml` |
| `correlationId` y `endpoint` en el MDC | `CorrelationIdFilter.java` |
| `user` en el MDC | `AuthenticatedUserMdcFilter.java` |
| JSON con el MDC como campos de primer nivel | `logback-spring.xml` + `logstash-logback-encoder` |

### Por qué dos filtros y no uno

El usuario no existe hasta que Spring Security valida el token, pero el `correlationId` y el `endpoint` tienen que estar presentes **también en los 401**, que nunca llegan a un controlador.

- `CorrelationIdFilter` se registra con `HIGHEST_PRECEDENCE`, por delante de la cadena de seguridad, y envuelve la petición entera. Es el que limpia el MDC en el `finally` — sin eso, el hilo devuelto al pool contamina la siguiente petición.
- `AuthenticatedUserMdcFilter` se inserta **dentro** de la cadena, detrás de `BearerTokenAuthenticationFilter`, que es el primer punto donde hay `SecurityContext`.

El `correlationId` entrante se valida contra `^[A-Za-z0-9._:-]{1,64}$` y se descarta si no encaja: la cabecera se devuelve al cliente y se escribe en cada línea de log, así que no puede transportar saltos de línea ni comillas (log forging).

---

## Verificación

### 1. Los 6 campos en una línea real

Petición con correlationId propio, autenticada como `inv_admin`:

```bash
curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: demo-obs4-001" \
     "http://localhost:8080/products?page=0&size=1"
```

Línea emitida por el backend (recortado el `message`):

```json
{"timestamp":"2026-07-22T07:22:56.544963808Z","message":"Serializing PageImpl instances as-is is not supported...","logger":"org.springframework.data.web.config.SpringDataJacksonConfiguration$PageModule$WarningLoggingModifier","thread":"http-nio-8080-exec-8","level":"WARN","traceId":"36d47ea8f8c54b4752509aa7496085e4","spanId":"819e0cddda9fa9e0","endpoint":"GET /products","correlationId":"demo-obs4-001","user":"inv_admin","service":"inventory-api"}
```

| Campo exigido | Valor |
|---|---|
| `traceId` | `36d47ea8f8c54b4752509aa7496085e4` |
| `spanId` | `819e0cddda9fa9e0` |
| `correlationId` | `demo-obs4-001` |
| `nivel` | `WARN` |
| `usuario` | `inv_admin` |
| `endpoint` | `GET /products` |

### 2. La cabecera se devuelve, también sin autenticación

```
GET /products  (con token)     → HTTP 200, X-Correlation-Id: demo-obs4-001
GET /api/products (sin token)  → HTTP 401, X-Correlation-Id: 6c636340-03b9-473b-80a9-1bfa20dbaa3c
```

El 401 lleva identificador generado: el filtro corre por delante de la cadena de seguridad, como se esperaba.

### 3. Loki ingiere los 14 servicios

```
GET /loki/api/v1/label/service/values
→ alertmanager, alloy, backend, frontend, grafana, keycloak, keycloak-db,
  loki, node-exporter, postgres, postgres-exporter, prometheus, redis, tempo
```

### 4. La línea es consultable desde Loki, con `level` como etiqueta

```
GET /loki/api/v1/query_range?query={service="backend"} |= "demo-obs4-001"
→ 1 stream: {container="inventory-backend", level="WARN", service="backend"}
```

Valores de la etiqueta `level`: `DEBUG`, `INFO`, `WARN`.

### 5. Grafana

```
GET /api/datasources/uid/loki/health
→ {"message":"Data source successfully connected.","status":"OK"}
```

Datasource provisionado con el derived field `traceId`, que enlaza cada línea con su traza en Tempo.

### 6. Pruebas unitarias

`CorrelationIdFilterTest` (5) y `AuthenticatedUserMdcFilterTest` (4). Suite completa: **276 tests, 0 fallos**.

---

## Limitaciones conocidas

**El JSON solo se emite en los perfiles `staging` y `prod`.** En `dev` —el valor por defecto de `.env`— los logs siguen siendo texto legible con `traceId` y `spanId` en el patrón, pero sin los campos estructurados. Loki los ingiere igualmente, sin parsear.

La verificación de arriba se hizo levantando el backend con `SPRING_PROFILES_ACTIVE=staging`. Para la demo de la Ola 6 hay que decidir explícitamente con qué perfil se levanta el stack; con `dev` el panel de logs de Grafana no puede filtrar por usuario ni por endpoint.

**El primer arranque de Alloy rechaza el histórico.** Alloy lee los logs de cada contenedor desde el principio y Loki rechaza lo anterior a `reject_old_samples_max_age` (168 h) con un 400. Es un error único de arranque, no una pérdida de datos vigentes: esas líneas quedarían fuera de la retención de todos modos.

**Alloy corre como `root`** para poder leer `/var/run/docker.sock`, que pertenece a `root:root` con permisos 660. El socket se monta en **solo lectura**: Alloy descubre contenedores y consume sus logs, nunca los controla.
