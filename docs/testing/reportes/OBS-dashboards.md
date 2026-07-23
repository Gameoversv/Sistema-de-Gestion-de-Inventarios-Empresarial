# Dashboards — separación en los cuatro exigidos

**Fecha:** 2026-07-22
**Entorno:** stack local en `feat/obs-4-loki-logs`, Grafana latest, Prometheus, Loki 3.3.2
**Estado:** verificado en vivo, panel por panel

---

## Resumen

Había **un** dashboard con 14 paneles mezclando JVM, HTTP y "negocio". El enunciado exige **cuatro**: Infraestructura, Aplicación, Negocio y Seguridad. Ahora hay cuatro archivos provisionados, con **37 consultas y ninguna vacía**.

El dashboard antiguo se borró en lugar de conservarse: mantenerlo habría duplicado todos los paneles de JVM y HTTP.

| Archivo | UID | Paneles |
|---|---|---|
| `01-infraestructura.json` | `inventory-infra` | 9 |
| `02-aplicacion.json` | `inventory-aplicacion` | 10 (1 de Loki) |
| `03-negocio.json` | `inventory-negocio` | 7 |
| `04-seguridad.json` | `inventory-seguridad` | 8 (1 de Loki) |

Los umbrales de color replican los de las alertas —CPU 80%, p95 500 ms, error rate 5%, 401 a 0,2/s— para que el panel y la alerta no puedan contradecirse.

---

## Verificación

Cada `expr` de cada panel se ejecutó contra la API de Prometheus y se contó el número de series devueltas. Se generó tráfico real primero: movimientos de stock de los tres tipos, dos cruces de mínimo, tres peticiones sin token (401), dos con token de `inv_auditor` sin `stock:manage` (403) y tres intentos de login con contraseña incorrecta.

**Resultado final: 0 paneles vacíos de 37 consultas.** Los dos paneles de logs se verificaron aparte contra la API de Loki.

### Tres paneles salían vacíos y se corrigieron

**1 · Disco — no había métricas de disco en absoluto.**
`node-exporter` solo montaba `/proc` y `/sys`, así que únicamente veía los `tmpfs` de su propio contenedor. El panel habría sido decorativo. Se añadió `--path.rootfs=/host/root` con `/:/host/root:ro`: ahora hay 23 series reales, incluido el volumen donde vive Docker. Cambiado además el filtro a lista blanca (`ext4|xfs|9p`) en vez de exclusiones, porque el exporter ve también binds de fuse que no dicen nada del espacio libre.

**2 · Error rate — vacío justo cuando todo va bien.**
`sum(rate(...{status=~"5.."}[5m]))` sobre un conjunto vacío no devuelve serie, así que la división entera se quedaba sin resultado y el panel aparecía en blanco precisamente cuando no había ningún 5xx. Corregido con `or vector(0)` en el numerador.

**3 · Cruces de mínimo — `increase()` no ve el primer cruce.**
El contador `inventory_stock_alerts_total{sku}` **nace** en el momento en que un producto cruza su mínimo por primera vez. Sobre una serie recién aparecida, `increase()` devuelve cero: Prometheus nunca observó el valor anterior y no puede saber que subió de 0 a 1. Verificado en vivo — tras provocar un cruce nuevo, `increase(...[15m])` seguía dando 0 para el SKU nuevo mientras el contador en bruto marcaba 1.

El panel pasa a mostrar el **acumulado con interpolación escalonada**: cada escalón es un cruce y su posición en el eje X es cuándo ocurrió. El primer cruce, que es el interesante, sí se ve.

> Esta limitación afecta también a los stats "(rango)" de Negocio, que usan `increase(...[$__range])`: el primer movimiento de cada tipo no queda contado hasta que hay una segunda muestra. Es comportamiento estándar de Prometheus con contadores nuevos, no un defecto de la consulta, y desaparece en cuanto el sistema lleva un rato en marcha.

---

## Hallazgo: Keycloak sí registra los fallos de login, aunque no los mida

El plan daba por perdida la detección de fuerza bruta contra el formulario de Keycloak (§3.4): `KC_METRICS_ENABLED` solo aporta métricas de Quarkus y un 401 del backend no ve los intentos contra el IdP.

Al construir el dashboard de Seguridad se comprobó que **Keycloak sí emite cada intento fallido como evento en el log**, con tipo, usuario, IP de origen y motivo. Provocando tres logins con contraseña incorrecta:

```
WARN [org.keycloak.events] type="LOGIN_ERROR" clientId="inventory-frontend"
     userId="0b1665c6-…" ipAddress="172.20.0.1"
     error="invalid_user_credentials" username="inv_admin"
WARN [org.keycloak.events] type="LOGIN_ERROR" … error="user_temporarily_disabled" …
```

El tercer intento devolvió `user_temporarily_disabled`: la protección de fuerza bruta del realm actuó y quedó registrada.

Alloy recoge estos logs y Loki los indexa, así que el panel "Keycloak — eventos de login y errores" cubre lo que el plan daba por no observable. **Consecuencia para el plan:** la mejora *[criterio propio] métricas de login de Keycloak vía SPI* (1,5 h, Ola de mejoras) deja de ser necesaria — la información ya es consultable, por logs en lugar de por métricas.

---

## Contenido de cada dashboard

**1 · Infraestructura** — CPU, memoria, load y disco del host; tráfico de red; los 5 targets de Prometheus con su estado; conexiones de PostgreSQL por estado, tamaño de las bases y locks por modo.

**2 · Aplicación** — Las cuatro señales doradas: throughput, latencia p95 (global y por endpoint), error rate y saturación (pool HikariCP, heap, GC, hilos). Cierra con un panel de logs del backend en WARN y ERROR, donde cada línea enlaza con su traza en Tempo por el `traceId`.

**3 · Negocio** — Movimientos confirmados por tipo, unidades que entran y salen, productos bajo mínimo ahora mismo y qué SKU cruzan su mínimo. Sustituye a los paneles que medían req/s por ruta y que no distinguían un listado de un movimiento real.

**4 · Seguridad** — 401 y 403 separados, porque significan cosas distintas: token ausente o inválido frente a token válido sin el scope necesario. Endpoints que concentran los rechazos, disponibilidad de Keycloak, tabla de alertas activas y los eventos de login del IdP.
