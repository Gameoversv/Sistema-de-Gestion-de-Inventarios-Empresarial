# SEC-2 y S-2 — Expiración de sesiones y refresh tokens

**Fecha:** 2026-07-23
**Entorno:** stack local, Keycloak 24.0, frontend con vitest 4.1.8
**Estado:** implementado y verificado en vivo

---

## Por qué juntos

El enunciado exige las dos cosas de forma explícita —*"refresh tokens"* y *"expiración de sesiones"*— y ninguna estaba cubierta. Son las dos caras del mismo ciclo de vida: qué hace el cliente cuando el token caduca, y que el servidor sepa renovarlo.

## SEC-2 — Expiración de sesiones

### Lo que había

El único manejo de caducidad vivía en el interceptor de respuesta de `frontend/src/lib/api.ts`:

```ts
if (error.response?.status === 401) {
  try {
    await keycloak.updateToken(30)
    ...
  } catch {
    keycloak.login()
  }
}
```

Es **reactivo**: solo se dispara si el usuario lanza una petición. Con la pestaña abierta y sin actividad, el token caducaba en silencio y el primer clic después de un rato fallaba con 401 antes de recuperarse. Y si también había caducado la sesión SSO, la renovación era imposible: el usuario se quedaba ante una interfaz que aparentaba estar autenticada.

`keycloak-js` emite dos avisos para esto —`onTokenExpired` y `onAuthRefreshError`— y no había ninguno conectado.

### Lo que se hizo

Módulo nuevo `frontend/src/lib/session.ts`:

```ts
export function installSessionHandlers(client: SessionClient): void {
  client.onTokenExpired = () => {
    void client.updateToken(MIN_TOKEN_VALIDITY_SECONDS).catch(() => endSession(client))
  }
  client.onAuthRefreshError = () => endSession(client)
}

export function endSession(client: SessionClient): void {
  client.clearToken()
  client.login()
}
```

Tres decisiones que no son obvias:

**El orden de `endSession`.** Primero `clearToken()`, después `login()`. Si se redirige primero, el token muerto sigue en memoria y cualquier petición en vuelo lo reutiliza y recibe un 401 innecesario.

**Los manejadores se instalan en `keycloak.ts`, no en `AuthContext`.** Deben estar puestos antes de `init()`, que se lanza a nivel de módulo, y así cualquier consumidor que importe el cliente hereda el comportamiento sin acordarse de instalarlo.

**La interfaz `SessionClient` se declara aparte del tipo de `keycloak-js`.** La instancia real es un singleton que se inicializa contra un servidor; declarar solo lo que el módulo necesita permite probarlo con un doble, sin red y sin jsdom pegándose con el iframe de Keycloak.

De paso, `api.ts` se alineó: el `30` estaba duplicado como número mágico y ahora es `MIN_TOKEN_VALIDITY_SECONDS`, y su rama de fallo llamaba a `login()` sin descartar el token. Ahora ambas rutas —la proactiva y la reactiva— terminan en la misma función.

### Verificación

TDD: los cinco tests se escribieron primero y fallaron por ausencia del módulo.

| Test | Qué fija |
|---|---|
| `renueva el token cuando Keycloak avisa de que ha caducado` | El caso normal: no se espera a que falle una petición |
| `manda al login cuando la renovación falla` | Sesión SSO caducada |
| `descarta el token caducado antes de redirigir al login` | **El orden**, que es la parte fácil de romper en un refactor |
| `cierra la sesión cuando Keycloak notifica un error de refresco` | La segunda vía, `onAuthRefreshError` |
| `no redirige cuando el token todavía era válido` | Que un `updateToken` que devuelve `false` no se trate como fallo |

```
Test Files  4 passed (4)
     Tests  15 passed (15)
```

`tsc -b` y `eslint` limpios.

## S-2 — Refresh tokens

### Dónde se prueba

En el paso *API smoke & integration tests* de `.github/workflows/staging.yml`, que ya corre **contra el sistema desplegado y contra Keycloak vivo**. Es donde el enunciado quiere las pruebas de seguridad, y evita montar un Keycloak solo para esto mientras TEST-1 (Testcontainers con Keycloak) siga pendiente.

La petición inicial de token descartaba el `refresh_token`:

```bash
TOKEN=$(curl -sf ... | jq -r '.access_token')
```

Ahora se guarda la respuesta completa y ambos tokens salen de la misma concesión.

### Las cinco comprobaciones

| # | Comprobación | Por qué |
|---|---|---|
| 1 | La concesión por password devuelve `refresh_token` | Sin él no hay nada que probar |
| 2 | `grant_type=refresh_token` devuelve un `access_token` | El flujo exigido |
| 3 | El token renovado **es distinto** del original | Uno idéntico significaría que la renovación no prolonga nada |
| 4 | El backend acepta el renovado en un endpoint con scope (`/api/audit/stock-movements` → 200) | Lo que importa no es que Keycloak emita algo, sino que el backend lo acepte **conservando los scopes** |
| 5 | Un `refresh_token` inválido se rechaza con 400 | Sin la negativa, un endpoint que devolviera token a cualquiera pasaría las cuatro anteriores |

La 4 y la 5 son las que dan valor. Las tres primeras las pasaría cualquier implementación que simplemente responda algo.

### Verificación en vivo

Ejecutado contra el stack local desde el host, que reproduce la topología de CI:

```
refresh_token presente : True
access_token nuevo     : True
distinto del original  : True
backend con renovado   : 200
refresh invalido       : 400
```

Más `bash -n` sobre el bloque extraído del workflow y validación de que el YAML parsea.

### Un falso negativo que conviene dejar anotado

La primera ejecución se hizo desde un contenedor en la red de compose, pidiendo el token a `http://keycloak:8080`. El backend respondió **401** al token renovado.

No era un fallo del flujo: `KC_ISSUER_URI` es `http://localhost:8180/realms/inventory`, así que un token pedido por el nombre interno lleva un `iss` que no casa y el backend lo rechaza, con razón. En CI ambas URLs son `localhost` y el problema no existe.

Vale la pena registrarlo porque es la clase de 401 que se atribuye al código cuando en realidad lo causa la ruta desde la que se pidió el token.

## Lo que no cubre

El test de S-2 corre en `staging.yml`, que se ejecuta al desplegar, no en cada PR. Hasta que TEST-1 traiga Testcontainers con Keycloak, la renovación no se verifica en el pipeline de integración continua.

Del lado del cliente, los manejadores están probados con un doble, no contra un Keycloak real. La confirmación de extremo a extremo —dejar la sesión caducar en el navegador y ver el redirect— corresponde al ensayo de P-3 (charter #60).
