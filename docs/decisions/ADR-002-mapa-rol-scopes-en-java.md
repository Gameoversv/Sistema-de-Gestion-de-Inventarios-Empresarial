# ADR-002 — El mapa rol→scopes vive en el backend, no en Keycloak

- **Estado:** Aceptado (medida de contención; ver [G-8](../PLAN_EJECUCION.md) para la corrección de raíz pendiente)
- **Fecha:** 2026-07-24
- **Autores:** Equipo de desarrollo
- **Revisores:** Docente

---

## Contexto

El enunciado exige un modelo de autorización granular donde *"cada operación crítica deberá verificar el permiso correspondiente"*, con siete permisos (`product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view`, `user:manage`, `audit:view`) emitidos como scopes de OAuth2 en el JWT.

Lo natural sería que Keycloak, como IdP, fuese la autoridad única: cada rol de realm tendría asignados sus scopes, y el backend se limitaría a confiar en el `scope` del token.

Durante el testing exploratorio (charter #58) se descubrió que **esa confianza no es segura en la configuración actual del realm**. El hallazgo, verificado en vivo ([informe G-6](../testing/reportes/G-6-escalada-de-scopes.md), issue #43):

> Keycloak emite **cualquier scope a cualquier usuario autenticado**. Los scopes personalizados están registrados como *optional client scopes*, y Keycloak los concede a quien los pida en la petición del token, **sin comprobar el rol de realm del usuario**. Un `inv_viewer` obtuvo `product:manage`, `stock:manage`, `user:manage` y `audit:view` simplemente pidiéndolos en el parámetro `scope`.

Si el backend confiara en el `scope` del token tal cual, la matriz de permisos no protegería nada: cualquiera podría auto-concederse los siete.

---

## Decisión

**El backend mantiene una tabla explícita rol→scopes permitidos e intersecta el `scope` del token contra ella.** Un scope que el rol del usuario no permite se descarta aunque Keycloak lo haya firmado.

La tabla vive en [`SecurityConfig.java`](../../backend/src/main/java/com/inventory/common/config/SecurityConfig.java) (`SCOPES_BY_ROLE`):

| Rol de realm | Scopes permitidos |
|---|---|
| `inventory-admin` | los siete |
| `warehouse-clerk` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
| `auditor` | `product:view`, `stock:view`, `report:view`, `audit:view` |
| `viewer` | `product:view`, `stock:view`, `report:view` |

Reglas de la intersección (`extractAuthorities` / `permittedScopesForRoles`):

- La autoridad `SCOPE_x` se concede **solo si** `x` está en el token **y** algún rol del usuario lo permite.
- Un usuario con varios roles recibe la **unión** de sus scopes permitidos.
- Un usuario sin ningún rol reconocido no recibe **ninguna** autoridad: se deniega por defecto.

Es una medida de **contención en el resource server**, no la solución correcta. La corrección de raíz es restringir la emisión en el propio IdP (`scopeMappings` por rol), tarea **G-8** del plan, tras la cual esta tabla pasaría a ser defensa en profundidad en vez del único control.

---

## Alternativas consideradas

| Alternativa | Razón de descarte |
|---|---|
| **Confiar en el `scope` del token tal cual** | Inseguro con el realm actual: G-6 demuestra que cualquiera obtiene cualquier scope. Sería no tener control de acceso |
| **Corregir solo en Keycloak (`scopeMappings`) y confiar en el token** | Es la solución correcta (G-8), pero mueve el control fuera del código revisable y depende de configuración del realm que hoy no está versionada de forma verificable. Se hará, pero mantener la comprobación en el backend como defensa en profundidad es deseable incluso después |
| **Keycloak Authorization Services (Resources, Policies, Permissions)** | El enunciado nombra "Policies", así que se evalúa aparte (**G-1**). Es un modelo más pesado; decidir su alcance es una decisión propia, no bloquea esta contención |
| **Autorizar por nombre de rol (`hasRole`)** | Prohibido por el enunciado: *"No se permitirá validar acceso únicamente por nombre de rol"*. Además no resuelve la escalada |

---

## Consecuencias

### Positivas

- El control de acceso efectivo es **código revisable y testeable**, no configuración dispersa en el IdP. `KeycloakJwtConverterTest` y `SecurityIntegrationTest` lo verifican en cada build, incluido el caso de rol desconocido.
- La escalada de G-6 queda **neutralizada en el backend** mientras G-8 no exista: pedir un scope de más no sirve de nada, se descarta.
- Denegar por defecto: sin rol reconocido, cero autoridades.

### Negativas / Riesgos

- **Duplicación aparente.** El mapa rol→scopes existe conceptualmente en dos sitios (el realm y el backend). Hasta G-8, el del realm es permisivo y el del backend es el que manda; esa asimetría hay que conocerla para no "simplificar" borrando la tabla de Java.
- **Punto único de verdad frágil.** Cualquiera que edite `SCOPES_BY_ROLE` sin entender G-6 puede abrir un agujero. Mitigado con el comentario en el código que apunta a este ADR y al informe.
- **No cubre la capa del frontend.** `AuthContext.tsx` calcula permisos con lógica de "primer rol gana" (**G-3a**, issue #44); falla del lado seguro (muestra de menos), pero es un defecto aparte que este ADR no resuelve.
- El frontend y Swagger deben pedir explícitamente los scopes en el `scope` de la petición del token; sin ellos, el backend responde 403 aunque el usuario tenga el rol. Documentado en el README.

---

## Referencias

- [Informe G-6 — Escalada de scopes](../testing/reportes/G-6-escalada-de-scopes.md)
- [Informe G-4/G-5 — Scopes por rol](../testing/reportes/G-4-G-5-scopes-por-rol.md)
- Issue #43 — Keycloak emite cualquier scope a cualquier usuario autenticado
- [RNF-02 — Autorización por permiso, no por rol](../requisitos/requisitos-no-funcionales.md#rnf-02--autorización-por-permiso-no-por-rol)
- `SecurityConfig.java` — `SCOPES_BY_ROLE`, `extractAuthorities`, `permittedScopesForRoles`
- [ADR Template — Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
