# Manual de Mantenimiento

Operar el sistema una vez instalado: qué vigilar, qué rutinas ejecutar, cómo respaldar y recuperar, y las trampas que muerden a quien no las conoce. Para instalarlo desde cero, ver la [guía de instalación](../arquitectura/guia-de-instalacion.md).

Este manual cubre la parte de *"manuales de mantenimiento"* del entregable de Documentación Técnica.

---

## Qué vigilar a diario

El sistema se observa a sí mismo. El trabajo de mantenimiento es mirar los cuatro dashboards de Grafana (`http://localhost:3001`) y atender lo que las alertas señalen, no revisar logs a mano.

| Dashboard | Responde a la pregunta |
|---|---|
| Infraestructura | ¿Hay CPU, memoria o disco al límite? |
| Aplicación | ¿Sube la latencia o el error rate? ¿El pool de conexiones se agota? |
| Negocio | ¿Cuántos movimientos, unidades y alertas de stock? ¿Productos bajo mínimo? |
| Seguridad | ¿Fallos de autenticación? ¿Eventos `LOGIN_ERROR` de Keycloak? |

### Las cinco alertas que exigen acción

Enrutadas a Alertmanager (`http://localhost:9093`), definidas en `observability/prometheus/rules/alerts.yml`:

| Alerta | Qué mirar primero |
|---|---|
| Alto consumo de CPU | Dashboard de Infraestructura; si es el backend, revisar carga de peticiones |
| Error rate elevado | Dashboard de Aplicación → trazas en Tempo del endpoint que falla |
| Latencia alta | Pool de conexiones (¿agotado?) y consultas lentas en `postgres-exporter` |
| Servicio caído | `docker compose ps`; el servicio marcará distinto de `healthy` |
| Fallos de autenticación | Dashboard de Seguridad; distinguir 401 legítimos de un patrón de fuerza bruta |

Hay una sexta, de negocio: `ProductosBajoMinimo`. No es un fallo técnico, es una señal operativa de reponer stock.

### Del síntoma a la causa, sin adivinar

La correlación traza↔log está provisionada en ambos sentidos. Desde una traza lenta en Tempo se salta a sus logs en Loki, y desde una línea de log al trace completo. Todo pivota sobre el `correlationId`, presente incluso en las peticiones que terminan en 401. No hace falta casar timestamps a ojo.

> El log filtrable por usuario y endpoint **solo existe en los perfiles `demo`, `staging` y `prod`**, que emiten JSON. En `dev` es texto plano y el pivote no funciona. Un entorno que se va a operar no corre en `dev`.

---

## Rutinas periódicas

| Cuándo | Tarea |
|---|---|
| Semanal | Revisar `docker compose ps`: ningún contenedor reiniciándose en bucle. Ojear el crecimiento de los volúmenes de Prometheus, Loki y Tempo |
| Mensual | `mvn dependency:tree` / `npm audit` y revisar el escaneo de dependencias del CI (T-5, cuando esté). Comprobar CVEs de las imágenes base |
| Por release | Etiqueta `vX.Y.Z`, que dispara `production.yml`, y smoke test post-despliegue |
| Ante cambio de esquema | **Nunca** editar tablas a mano: nueva migración `V{n+1}__*.sql` |

### Retención de telemetría

Prometheus, Loki y Tempo escriben en volúmenes locales sin límite de retención configurado. En una instalación de larga vida crecen sin techo. Antes de un despliegue serio, fijar retención (`--storage.tsdb.retention.time` en Prometheus y equivalentes) o los volúmenes acabarán llenando el disco del host — que es el mismo que aloja los datos de negocio.

---

## Cambios de esquema de base de datos

La regla es única y no admite excepción: **el esquema solo cambia por migración Flyway versionada.**

- La aplicación arranca con `ddl-auto: validate` y `validate-on-migrate: true`: **se niega a arrancar** si el esquema no coincide. Un fallo de arranque tras un cambio suele ser esto.
- Añadir una migración: crear `backend/src/main/resources/db/migration/V8__descripcion.sql`. Numeración correlativa; `out-of-order` está desactivado.
- **No editar una migración ya aplicada.** Flyway valida el checksum de cada una; cambiar una migración pasada rompe el arranque en cualquier entorno donde ya corrió.
- El rollback no es automático. Revertir es otra migración hacia adelante (`V9__revert_*.sql`), no un `undo`.

Las siete actuales van de `V1` (esquema inicial) a `V7` (snapshots de movimiento). Las tablas `*_aud` de auditoría las crea `V4`.

---

## Respaldo y recuperación

### Qué respaldar

Dos bases de datos, en dos volúmenes distintos:

- `postgres_data` — datos de negocio: productos, stock, movimientos, auditoría. **El que importa.**
- `keycloak_db_data` — usuarios, roles y configuración del realm.

El resto de volúmenes (telemetría, cache) es reconstruible y no necesita respaldo.

### Respaldo en caliente

```bash
# Datos de negocio
docker compose exec -T postgres \
  pg_dump -U inventory_user inventory_db | gzip > backup-negocio-$(date +%F).sql.gz

# Realm de Keycloak
docker compose exec -T keycloak-db \
  pg_dump -U keycloak_user keycloak_db | gzip > backup-keycloak-$(date +%F).sql.gz
```

### Restauración

```bash
gunzip -c backup-negocio-2026-07-24.sql.gz | \
  docker compose exec -T postgres psql -U inventory_user -d inventory_db
```

Restaurar sobre una base con datos exige vaciarla antes, o los datos se mezclan. Para recuperación total, `down -v`, `up` limpio y restaurar sobre el esquema recién migrado.

---

## La trampa del volumen de Keycloak

El error más caro de operar este sistema, y el menos obvio. Va con detalle porque cuesta horas cuando pilla desprevenido.

**El síntoma.** Se toca la configuración del realm —un usuario, un scope, un cliente— y el cambio **no aparece**, o al reiniciar el stack vuelve el estado viejo. O peor: `keycloak-init` falla con `duplicate key … uk_cli_scope`.

**La causa.** La configuración de Keycloak entra por **dos vías que se pisan**:

1. **Declarativa** — `keycloak/realm-export.json`, montado solo lectura, importado con `--import-realm` **solo si el realm no existe todavía**.
2. **Imperativa** — `scripts/keycloak/init-users.sh`, que corre cada vez y crea scopes y usuarios vía Admin API.

Y sobre todo: el realm **persiste en el volumen `keycloak_db_data`**. Una vez creado, `--import-realm` no vuelve a tocarlo. Editar `realm-export.json` y reiniciar **no cambia nada**, porque Keycloak ya tiene su realm en la base y no reimporta.

**Las consecuencias, según lo que se quiera:**

| Objetivo | Acción correcta |
|---|---|
| Aplicar un cambio de `realm-export.json` | Borrar el volumen: `docker compose down -v` (o solo `keycloak_db_data`) y volver a levantar. **Sin esto el cambio se ignora** |
| Un arranque de demo reproducible | `down -v && up` — realm recreado desde cero, limpio |
| Conservar cambios hechos a mano en la consola | **No** hacer `down -v`: se pierden. Solo persisten en el volumen |

**Por qué `keycloak-init` no es idempotente (P-2b).** El script crea scopes con `type: none` y, al reejecutarse sobre un realm que ya los tiene, choca contra la restricción única `uk_cli_scope`. En un `down -v && up` limpio no ocurre, porque el realm nace vacío. En un `up` sobre un volumen existente, sí: es inofensivo salvo que aborta la inicialización y ensucia el panel de eventos. Es la issue #45; corrección pendiente, 30 minutos.

**Regla práctica:** cualquier cambio de configuración del realm que deba ser permanente va en `realm-export.json` **o** en `init-users.sh`, nunca solo en la consola web, y se prueba con un `down -v && up`. Lo que solo vive en el volumen se pierde en la primera recuperación.

---

## Gestión de secretos

- Los secretos reales viven en `.env` (local, en `.gitignore`) y en los secretos de repositorio del CI. **Nunca en el código.**
- `.env.example` documenta cada variable con un valor `changeme_*`: sirve de plantilla, no de configuración.
- Rotar un secreto: cambiarlo en `.env`, `docker compose up -d` del servicio afectado, y en el CI actualizar el secreto de repositorio.

**Deuda abierta (S-4b, issue #47):** `.github/workflows/staging.yml` todavía inyecta `JWT_SECRET` y `JWT_EXPIRATION_MS`, restos de un esquema de autenticación abandonado. Es un secreto de repositorio **vivo que no protege nada**. Retirarlo es limpieza pendiente de 10 minutos; mientras siga, contradice la regla de "nada de configuración decorativa".

---

## Operaciones frecuentes

| Necesidad | Comando |
|---|---|
| Ver el estado de todo | `docker compose ps` |
| Logs de un servicio | `docker compose logs -f <servicio>` |
| Reiniciar solo el backend (tras cambiar perfil) | `docker compose up -d backend` |
| Reconstruir tras cambio de código | `docker compose up -d --build backend` |
| Aplicar cambio de realm | `docker compose down -v && docker compose up -d` |
| Recuperación total desde respaldo | `down -v` → `up` → restaurar dump |

Los identificadores entre paréntesis (P-2b, S-4b, C-4…) son issues de GitHub y tareas del [plan de ejecución](../PLAN_EJECUCION.md).
