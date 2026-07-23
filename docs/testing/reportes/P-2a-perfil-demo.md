# P-2a — Perfil `demo`: la presentación deja de depender de un `.env` editado a mano

**Fecha:** 2026-07-23
**Entorno:** stack local en `fix/p-2a-perfil-demo`, backend reconstruido, Postgres / Redis / Keycloak levantados
**Estado:** corregido y verificado en vivo

---

## El problema

La demo se levantaba con `SPRING_PROFILES_ACTIVE=staging`, porque es el único perfil junto a `prod` que emite JSON estructurado: sin él, el panel de logs de Grafana no puede filtrar por usuario ni por endpoint y el dashboard de Seguridad se queda ciego.

Pero `application-staging.yml` fija el CORS a `https://staging.inventory.example.com`. Contra un frontend servido en `http://localhost:3000` el navegador bloquea **toda** petición. Para P-2 bastó con no usar la interfaz y añadir dos overrides al `.env` local:

```
APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
```

Eso no vale para **P-3**, cuyo requisito es levantar el stack desde cero con `docker compose down -v && docker compose up`. `.env` está en `.gitignore`, así que los overrides no viajan con el repositorio: en otra máquina —o en la del profesor— el ensayo arranca con el frontend bloqueado y sin ninguna pista de por qué.

## Las tres salidas y por qué se descartaron dos

| Opción | Por qué no |
|---|---|
| Añadir `localhost:3000` a `staging.yml` | Ese perfil declara en su cabecera que espeja producción. Un origen local ahí es un permiso que se arrastra al despliegue real, y `staging.yml` sí se usa en el workflow de despliegue |
| Volver a `dev` | Emite texto plano: se pierden los seis campos MDC y con ellos los paneles de Negocio y Seguridad, que es exactamente lo que P-2 acababa de dejar demostrado |
| **Perfil `demo` propio** | **Elegida.** Aísla la demo de los perfiles de despliegue |

## Lo que se cambió

| Archivo | Cambio |
|---|---|
| `backend/src/main/resources/application-demo.yml` | Nuevo. Copia de `staging` con dos diferencias: CORS a `http://localhost:3000` y muestreo de trazas a `1.0` |
| `backend/src/main/resources/logback-spring.xml` | `demo` entra en la rama JSON (`staging \| prod \| demo`) y se añade a la negación del fallback |
| `.env.example` | Documenta los cuatro perfiles y para qué sirve cada uno. Antes no decía nada y `dev` parecía la única opción sensata |
| `backend/src/test/java/.../CorsProfilesTest.java` | Nuevo. Fija el origen de cada perfil |

El muestreo a `1.0` no es cosmético: `staging` muestrea al 50 %, y en una demo de pocos minutos eso deja la mitad de los paneles de Tempo sin trazas que enseñar.

## Verificación

Todo contra el contenedor recién construido, no contra la configuración leída.

**1. El perfil que arranca es `demo`:**

```
{"message":"The following 1 profile is active: \"demo\"","logger":"com.inventory.InventoryApplication","level":"INFO","service":"inventory-api"}
```

Que esa línea salga ya en JSON es la prueba de que la rama de logback funciona: bajo `dev` habría salido como texto coloreado.

**2. El frontend local pasa el preflight:**

```
$ curl -i -X OPTIONS http://localhost:8080/api/products \
    -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: GET"
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:3000
```

**3. Un origen ajeno sigue rechazado** — el perfil abre localhost, no el CORS entero:

```
$ curl -i -X OPTIONS http://localhost:8080/api/products \
    -H "Origin: https://evil.example.com" -H "Access-Control-Request-Method: GET"
HTTP/1.1 403
```

**4. La cadena de observabilidad sigue en pie:**

```
$ curl -i http://localhost:8080/health -H "Origin: http://localhost:3000"
HTTP/1.1 200
X-Correlation-Id: fe93d88d-c2bd-461e-870c-cb798fa988a3
Access-Control-Allow-Origin: http://localhost:3000
```

`CorrelationIdFilter` corre y las líneas de log llevan `traceId` y `spanId` propagados por Micrometer.

**5. Los cuatro tests de `CorsProfilesTest` en verde**, con `spotless` incluido en la misma ejecución:

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Sobre el test

Las dos aserciones que sostienen la corrección son las negativas: `stagingProfile_rejectsLocalOrigins` y `prodProfile_rejectsLocalOrigins`. Que `demo` permita localhost no protege de nada; lo que hacía falta era impedir que el próximo que tropiece con el CORS lo "arregle" metiendo localhost en `staging` o en `prod`, que era la salida fácil y la equivocada.

Se resuelve con `ApplicationContextRunner` más `ConfigDataApplicationContextInitializer`: lee los YAML por perfil sin arrancar contexto ni tocar base de datos. Los cuatro tests tardan 2,6 s y no dependen de Docker, así que corren en el job de unit tests de CI y no en el de integración.

## Lo que queda alcanzado y lo que no

**Alcanzado.** P-3 deja de estar bloqueado: `down -v && up` con `SPRING_PROFILES_ACTIVE=demo` arranca con interfaz y con logs filtrables, sin editar nada a mano.

**Pendiente, y no es de este cambio.** El ensayo completo de P-3 sigue por hacer, y **P-2b** —`keycloak-init` no es idempotente y lanza `duplicate key … uk_cli_scope` al reejecutarse sobre un realm existente— sí afecta a un `down -v && up` repetido. No lo toca este PR.

## Verificación pendiente de la demo real

Los seis campos MDC (`traceId`, `spanId`, `correlationId`, `user`, `endpoint`, `level`) se verificaron bajo `staging` en el [informe de P-2](P-2-capturas-de-evidencia.md). Aquí se comprobó que el filtro sigue activo y que la salida es JSON, pero `user` y `correlationId` solo aparecen como campos en líneas emitidas **durante** una petición autenticada, y las peticiones de esta verificación no lo eran. La rama de logback es literalmente la misma que usa `staging`, así que no hay motivo para esperar diferencia; queda confirmarlo con tráfico real en el ensayo de P-3.
