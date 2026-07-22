# G-4 y G-5 — Unión de scopes y denegación por defecto

**Fecha:** 2026-07-22
**Entorno:** stack local, Keycloak 24.0, backend Spring Boot 3.3.5
**Archivo:** `backend/src/main/java/com/inventory/common/config/SecurityConfig.java`
**Estado:** corregido y verificado contra el sistema en ejecución

Contexto: [G-6 — Escalada de scopes](G-6-escalada-de-scopes.md). Keycloak emite scopes sin comprobar el rol, de modo que el techo por rol del backend es el control efectivo. Estos dos defectos lo debilitaban.

---

## G-4 — Ganaba el primer rol, no la unión

El método comprobaba los roles en cascada y retornaba en la primera coincidencia:

```java
if (roles.contains("inventory-admin")) { return Set.of(...); }
if (roles.contains("warehouse-clerk")) { return Set.of(...); }
if (roles.contains("auditor"))         { return Set.of(...); }
```

Un usuario con `warehouse-clerk` **y** `auditor` se quedaba en la rama de clerk y perdía `audit:view`, pese a tener el rol que lo concede.

### Verificación

Se añadió el rol `auditor` a `inv_clerk` y se pidió un token con ambos conjuntos de scopes:

```
roles: ['auditor', 'warehouse-clerk']

GET  /api/audit/stock-movements       HTTP 200   (antes: 403)
POST /products                        HTTP 201
GET  /api/reports/dashboard-metrics   HTTP 200
```

---

## G-5 — El fallback concedía lectura a cualquiera

```java
// viewer and any other role: read-only
return Set.of("product:view", "stock:view", "report:view", "openid", "email", "profile");
```

Cualquier rol no contemplado —o ningún rol— recibía lectura de productos, stock e informes. Combinado con G-6, un usuario recién creado sin rol de negocio obtenía acceso de lectura completo.

### Verificación

Usuario `inv_sinrol`, creado solo con los roles por defecto del realm:

```
roles realm  : ['offline_access', 'uma_authorization', 'default-roles-inventory']
SCOPE EMITIDO: openid profile email product:view report:view stock:view product:manage

GET /products                        HTTP 403   (antes: 200)
GET /api/stock/movements             HTTP 403   (antes: 200)
GET /api/reports/dashboard-metrics   HTTP 403   (antes: 200)
```

Keycloak sigue emitiendo los scopes; el backend ya no los honra.

---

## Corrección

Tabla declarativa por rol más unión explícita, con `viewer` como rol de primera clase en lugar de caso por defecto:

```java
private static final Map<String, Set<String>> SCOPES_BY_ROLE = Map.of(...);

private Set<String> permittedScopesForRoles(Set<String> roles) {
  Set<String> permitted = new HashSet<>();
  for (String role : roles) {
    Set<String> roleScopes = SCOPES_BY_ROLE.get(role);
    if (roleScopes != null) {
      permitted.addAll(roleScopes);
    }
  }
  if (permitted.isEmpty()) {
    return Set.of();
  }
  permitted.addAll(BASE_SCOPES);
  return Set.copyOf(permitted);
}
```

Ningún endpoint exige `openid`, `email` ni `profile` —solo los seis scopes de negocio—, por lo que negarlos a quien no tiene rol reconocido no rompe ningún flujo.

---

## Nota sobre los tests modificados

Dos casos de `KeycloakJwtConverterTest` afirmaban el comportamiento vulnerable y se reescribieron:

| Test | Qué afirmaba |
|---|---|
| `scopes_mappedWithScopePrefix` | Un JWT **sin ningún rol** debía recibir sus scopes |
| `rolesAndScopes_bothMapped` | El rol `manager`, inexistente en este realm, debía recibir scopes |

Ambos codificaban el fallo que G-5 corrige. En su lugar hay ahora seis casos que cubren: unión multi-rol, denegación sin rol reconocido, rol desconocido, viewer intentando escalar, administrador completo y conservación de los scopes OIDC base.

---

## Pendiente

La corrección de raíz sigue siendo de Keycloak: definir `scopeMappings` que restrinjan qué rol puede solicitar cada scope. Mientras no exista, esta tabla es el único control y no debe eliminarse.
