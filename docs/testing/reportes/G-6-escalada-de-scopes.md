# G-6 — Escalada de scopes en Keycloak

**Fecha:** 2026-07-22
**Entorno:** stack local en `main` (`b6bdc9f`), Keycloak 24.0, backend Spring Boot 3.3.5
**Severidad:** ALTA — mitigada por el backend, no por el proveedor de identidad
**Estado:** verificado en vivo

---

## Resumen

Keycloak emite **cualquier scope solicitado a cualquier usuario autenticado**, sin comprobar su rol de realm. Un usuario con rol `viewer` puede obtener un token que declara `product:manage`, `stock:manage`, `user:manage` y `audit:view`.

El acceso no se concede porque el backend descarta los scopes que el rol no puede tener. Esa comprobación en Java es hoy **el único control efectivo**.

---

## Causa

`scripts/keycloak/init-users.sh:41-66` crea los siete scopes y los enlaza a `inventory-frontend` como **opcionales**, sin asociarlos a ningún rol. El realm no define `scopeMappings` ni `clientScopeMappings`.

Un client scope opcional sin restricción de rol queda disponible para cualquiera que lo pida con el parámetro `scope`. `inventory-frontend` es cliente público con `directAccessGrantsEnabled: true`, de modo que la petición se puede hacer con un simple `curl`.

---

## Reproducción

### 1. Solicitar scopes elevados como `inv_viewer`

```bash
curl -X POST 'http://localhost:8180/realms/inventory/protocol/openid-connect/token' \
  -d 'client_id=inventory-frontend' \
  -d 'username=inv_viewer' -d 'password=Viewer123' \
  -d 'grant_type=password' \
  -d 'scope=openid product:view product:manage stock:manage user:manage audit:view'
```

Claims del `access_token` emitido:

```
usuario      : inv_viewer
roles realm  : ['viewer', 'offline_access', 'uma_authorization', 'default-roles-inventory']

SCOPE PEDIDO : openid product:view product:manage stock:manage user:manage audit:view
SCOPE EMITIDO: openid email stock:manage user:manage product:view audit:view profile product:manage
```

Keycloak concede los cuatro scopes elevados a un usuario de solo lectura.

### 2. Usar ese token contra el backend

| Petición | Esperado | Obtenido |
|---|---|---|
| `POST /products` | 403 | **403** |
| `GET /api/audit/stock-movements` | 403 | **403** |
| `GET /products` | 200 | **200** |

```json
{"type":"https://inventory.api/problems/access-denied","title":"Access Denied",
 "status":403,"detail":"Access denied","instance":"/products"}
```

---

## Por qué el backend aguanta

`SecurityConfig.java:146-153` intersecta los scopes del token con los permitidos para el rol:

```java
Set<String> permitted = permittedScopesForRoles(roles);
String scope = jwt.getClaimAsString("scope");
if (scope != null && !scope.isBlank()) {
  for (String s : scope.split(" ")) {
    if (!s.isBlank() && permitted.contains(s)) {
      authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
    }
  }
}
```

Sin esa intersección, los scopes elevados se convertirían en authorities y `@PreAuthorize` los aceptaría.

---

## Consecuencias

1. **El mapa Java no es duplicación redundante.** Es el control de seguridad activo. Eliminarlo para "leer solo lo que manda Keycloak" abriría la escalada.
2. **El test de RBAC de `staging.yml:164-174`** —"Viewer role blocked from POST /products"— pasa gracias a este mapa. Seguiría verde aunque Keycloak empeorase.
3. **Dos defectos agravan el cuadro** (pendientes, ver G-4 y G-5):
   - `SecurityConfig.java:190` — el fallback devuelve permisos de lectura a roles desconocidos, en vez de denegar por defecto.
   - `SecurityConfig.java:159-188` — gana el primer rol, no la unión: un usuario con `warehouse-clerk` y `auditor` pierde `audit:view`.

---

## Corrección recomendada

**Origen del problema (Keycloak):** definir `scopeMappings` que aten cada client scope a los roles autorizados a solicitarlo. Es la corrección de raíz y permitiría prescindir del mapa Java.

**Mientras tanto (backend):** corregir G-4 (unión de scopes) y G-5 (denegar por defecto), y dejar constancia por ADR de por qué el mapa existe.

**Orden obligatorio:** primero Keycloak, después el backend. Retirar el mapa antes de restringir los scopes en el IdP deja el sistema abierto.
