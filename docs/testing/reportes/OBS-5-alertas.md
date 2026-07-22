# OBS-5 — Las cinco alertas obligatorias

**Fecha:** 2026-07-22
**Entorno:** stack local, Prometheus + Alertmanager v0.27.0
**Requisito:** `Proyecto_Final_V3.pdf` — *"Alertas: Alto consumo CPU, Error rate elevado, Latencia alta, Servicios caídos y Fallos de autenticación"*
**Estado:** implementadas y verificadas de extremo a extremo

---

## Reglas implementadas

`observability/prometheus/rules/alerts.yml`, agrupadas por componente.

| Alerta | Expresión | Umbral | `for` | Severidad |
|---|---|---|---|---|
| AltoConsumoCPU | `100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)` | > 80 % | 5m | warning |
| ServicioCaido | `up == 0` | — | 1m | critical |
| ErrorRateElevado | proporción de `status=~"5.."` sobre el total | > 5 % | 5m | critical |
| LatenciaAlta | `histogram_quantile(0.95, ...)` | > 0,5 s | 5m | warning |
| FallosDeAutenticacion | `sum(rate(...{status="401"}[5m]))` | > 0,2 req/s | 2m | warning |

Cada regla lleva una anotación `runbook` con los pasos de diagnóstico, que es lo que el operador necesita cuando la alerta suena de madrugada.

### Sobre "fallos de autenticación"

El enunciado pide *fallos de autenticación*. Un **401** es exactamente eso: token ausente, inválido o expirado. Los **403** son fallos de *autorización* —token válido, permiso insuficiente— y se vigilan en el dashboard de Seguridad, no en esta alerta.

Se evaluó usar métricas de login de Keycloak, pero Keycloak 24 no las expone: `/metrics` solo publica series de Quarkus (`agroal`, `base`, `jvm`, `netty`, `process`, `system`, `vendor`, `worker`), sin ninguna de eventos de usuario. Habría requerido un SPI de terceros para un requisito que el 401 ya satisface.

---

## Alertmanager

`observability/alertmanager/alertmanager.yml`:

- **Agrupación** por `alertname` y `componente`, para no recibir una notificación por instancia
- **Ruta crítica** con `group_wait` de 10 s y repetición horaria, frente a 30 s / 4 h del resto
- **Regla de inhibición**: `ServicioCaido` silencia `LatenciaAlta` del mismo componente, porque la latencia es consecuencia y no causa

El destino de entrega depende del entorno y se inyecta por variable en staging y producción. En desarrollo los receptores no entregan a ninguna parte: la evidencia se toma de las interfaces de Prometheus y Alertmanager.

---

## Verificación

### 1. Reglas cargadas

```
[aplicacion]      ErrorRateElevado, LatenciaAlta
[infraestructura] AltoConsumoCPU, ServicioCaido
[seguridad]       FallosDeAutenticacion
total: 5

alertmanagers activos: ['http://alertmanager:9093/api/v2/alerts']
```

### 2. FallosDeAutenticacion — disparada provocando el fallo

880 peticiones sin autenticar contra `GET /products` durante 200 s:

```
tasa de 401: 3,06 req/s   (umbral 0,2)
regla:       FallosDeAutenticacion  →  firing
```

### 3. ServicioCaido — disparada deteniendo un servicio

`docker compose stop node-exporter`, esperando a que expire el `for: 1m`:

```
>>> ServicioCaido            firing
>>> FallosDeAutenticacion    firing
```

### 4. Entrega a Alertmanager con enrutado por severidad

```
ServicioCaido            sev=critical  comp=infraestructura  active
FallosDeAutenticacion    sev=warning   comp=seguridad        active
```

La cadena completa —Prometheus evalúa, la regla dispara, Alertmanager recibe y enruta según severidad— queda demostrada.

---

## Nota de operación

Recrear el contenedor de Prometheus con `docker compose restart` **no** aplica montajes nuevos: las reglas cargaban en cero pese a estar el archivo en su sitio. Hace falta `docker compose up -d prometheus`.

Además, el reinicio brusco corrompió los chunks del TSDB (`out of sequence m-mapped chunk`) y Prometheus los descartó al arrancar. No afecta a la configuración, pero conviene recogerlo en el manual de mantenimiento.
