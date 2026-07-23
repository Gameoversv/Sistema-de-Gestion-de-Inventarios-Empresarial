# P-2 — Capturas de los cuatro dashboards y de una alerta disparada

**Fecha:** 2026-07-22
**Entorno:** stack local completo (15 contenedores) en `main`, perfil `staging`, Grafana latest, Prometheus 15 s de scrape
**Estado:** seis capturas generadas y revisadas una a una

---

## Resumen

El entregable pedía capturas de los cuatro dashboards **con datos reales** y de **una alerta disparada**. Están en [`docs/testing/capturas/`](../capturas/):

| Archivo | Contenido | Paneles |
|---|---|---|
| `01-infraestructura.png` | CPU, memoria, disco, red, targets, PostgreSQL | 9, ninguno vacío |
| `02-aplicacion.png` | Señales doradas + logs correlacionados | 10, ninguno vacío |
| `03-negocio.png` | Movimientos, unidades, cruces de mínimo por SKU | 7, ninguno vacío |
| `04-seguridad.png` | 401/403, endpoints rechazados, eventos de Keycloak | 8, ninguno vacío |
| `05-prometheus-alertas.png` | `ProductosBajoMinimo` en estado FIRING | — |
| `06-alertmanager.png` | La misma alerta ya enrutada y agrupada | — |

La alerta no se capturó solo en Prometheus: `06-alertmanager.png` demuestra que **llegó hasta Alertmanager** con sus etiquetas de enrutado (`componente="negocio"`, `severity="warning"`, `profile="staging"`), que es lo que prueba que la cadena regla → evaluación → enrutado funciona entera.

La captura se automatizó en [`scripts/capturar-evidencia-observabilidad.mjs`](../../../scripts/capturar-evidencia-observabilidad.mjs) para poder repetirla antes de la presentación sin rehacer el proceso a mano.

---

## Tráfico generado

Los paneles no significan nada sin carga detrás. Se generó en tres fases:

1. **Alta de datos** — 1 categoría y 5 productos, dos de ellos ya por debajo de su mínimo, más 18 movimientos de los tres tipos y lecturas con los cuatro usuarios.
2. **Rechazos** — 572 peticiones con token inválido o sin cabecera (401), 143 logins correctos y 143 fallidos contra Keycloak, sostenidos durante 4 minutos.
3. **Movimientos repartidos** — 54 movimientos más, uno cada 20 s durante 6 minutos.

La tercera fase existe por una razón concreta, explicada abajo.

---

## Cuatro cosas que hubo que corregir

### 1 · Los paneles `increase()` marcaban 0 con el contador en 11

Los stats "(rango)" de Negocio daban `IN 0 · OUT 0 · ADJUSTMENT 0` mientras `inventory_stock_movements_total` valía 6, 11 y 1.

Es la limitación que [OBS-dashboards](OBS-dashboards.md) ya documenta, vista desde el otro lado: los 18 movimientos de la fase 1 cayeron **dentro de un mismo scrape de 15 s**, así que la serie nació valiendo 6 y se quedó plana. Prometheus no puede calcular el incremento del primer punto de una serie y devuelve 0.

No se toca la consulta —el comportamiento es correcto— sino el tráfico: repartiendo los movimientos en el tiempo, los mismos paneles pasan a `IN 31,3 · ADJUSTMENT 5,05 · OUT 2,02`.

**Consecuencia para la demo:** si en la presentación se hacen todos los movimientos seguidos, los paneles de Negocio saldrán en cero delante del tribunal. Hay que espaciarlos o dejar el stack corriendo un rato antes.

### 2 · Series duplicadas al cambiar de perfil

Con la ventana de 1 h, "Uptime del backend" pintaba **dos** tiles (35,2 y 29,3 min), el pool Hikari repetía `activas/activas` y GC repetía `hilos vivos`.

Las métricas llevan la etiqueta `profile`, así que un rango que abarque el cambio `dev` → `staging` contiene las dos series a la vez y Grafana las dibuja las dos. No es un fallo de los dashboards: es que la ventana cruzaba un reinicio con etiquetas distintas.

Las capturas se tomaron con `now-25m`, ya dentro de `staging`. El script deja la ventana configurable por `CAPTURA_DESDE` y avisa de esto en un comentario.

### 3 · El dashboard de Aplicación salía en blanco

La primera captura de `02-aplicacion.png` tenía las cabeceras de fila y nada debajo, pese a que el DOM sí contenía los 10 paneles.

Dos causas encadenadas: Grafana monta los paneles por scroll, y `fullPage: true` hace que Chromium redimensione el viewport durante la captura, con lo que Grafana desmonta y vuelve a montar los paneles justo mientras se toma la imagen. Solo fallaba el dashboard más alto.

Corregido recorriendo el dashboard antes de capturar y, sobre todo, **agrandando el viewport hasta que quepa entero** para capturar sin `fullPage`.

La primera versión del script decía "paneles sin datos: 0" sobre esa imagen vacía: contaba paneles vacíos sin comprobar que hubiera paneles. Ahora informa del total y devuelve código de salida 1 si algún dashboard sale vacío o con paneles sin datos.

### 4 · El panel de eventos de Keycloak mostraba ruido, no logins

En la primera pasada, "Keycloak — eventos de login y errores" solo contenía errores de Hibernate:

```
ERROR [org.hibernate.engine.jdbc.spi.SqlExceptionHelper]
      duplicate key value violates unique constraint "uk_cli_scope"
```

Vienen de `keycloak-init`, que **no es idempotente**: al reejecutarse sobre un realm que ya existía, reintenta insertar los mismos client scopes. No rompe el arranque —el script termina con `✅ Keycloak initialization complete`— pero ensucia el panel que debería mostrar logins.

Tras generar logins reales, el panel muestra lo que debe: `type="LOGIN_ERROR"`, `error="user_not_found"`, `username="usuario_inexistente_p2"`, con IP y cliente.

> Los logins fallidos se hicieron con un usuario **inexistente** a propósito. La protección de fuerza bruta del realm bloquea cuentas reales: en una sesión anterior dejó a `inv_admin` con `user_temporarily_disabled`.

---

## Verificación de la alerta

`ProductosBajoMinimo` (`inventory_products_below_minimum > 0`, `for: 10m`) recorrió el ciclo completo:

| Momento | Estado |
|---|---|
| Cruce de mínimo de `P2-SSD-004` y `P2-CAM-005` | métrica pasa a 4 |
| +15 s | `pending` |
| +10 min | `firing` |
| Enrutado | visible en Alertmanager, grupo `default` |

`FallosDeAutenticacion` (401 > 0,2 req/s durante 2 min) también llegó a `firing` con las 572 peticiones rechazadas, y volvió a `inactive` sola al cesar el tráfico, que es justo lo que debe hacer.

---

## Lo que sigue abierto

**El perfil `staging` deja la interfaz inservible en local.** `application-staging.yml` fija
`app.cors.allowed-origins: https://staging.inventory.example.com`, así que el frontend de
`localhost:3000` queda bloqueado por CORS. Para estas capturas se sorteó con un override en `.env`
(`APP_CORS_ALLOWED_ORIGINS`), que no está versionado.

Afecta a **P-1 y P-3**, que sí necesitan la interfaz: hay que decidir entre añadir el origen local a
`staging`, crear un perfil `demo`, o presentar con `dev` asumiendo que el panel de logs pierde el
filtrado por usuario y endpoint. Sin resolverlo, el ensayo desde cero se encuentra el frontend roto.

`MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0` es el segundo override temporal: `staging` muestrea la
mitad de las trazas, lo que basta para operar pero no para enseñar una traza concreta en clase.
