# ADR-004 — Keycloak es la autoridad única sobre los scopes del token

- **Estado:** Aceptado
- **Fecha:** 2026-07-25
- **Sustituye a:** [ADR-002 — El mapa rol→scopes vive en el backend](ADR-002-mapa-rol-scopes-en-java.md)
- **Autores:** Equipo de desarrollo
- **Revisores:** Docente

---

## Contexto

ADR-002 decidió que el backend mantuviera una tabla explícita rol→scopes (`SCOPES_BY_ROLE` en
`SecurityConfig.java`) e intersectara contra ella el claim `scope` del token. No era la solución
correcta y el propio ADR lo decía: era **contención**. El motivo era el hallazgo G-6 (issue #43),
verificado en vivo: Keycloak emitía **cualquier scope a cualquier usuario autenticado**, porque los
scopes de negocio están registrados como *optional client scopes* y Keycloak los concedía a quien
los pidiera en el parámetro `scope`, sin mirar el rol de realm. Un `inv_viewer` obtuvo
`product:manage`, `stock:manage`, `user:manage` y `audit:view` con solo pedirlos.

Con ese realm, confiar en el token habría dejado la matriz de siete permisos sin proteger nada.

**Lo que cambió: G-8.** `scripts/keycloak/init-users.sh` ata ahora cada client scope a los roles de
realm autorizados a solicitarlo (`scope-mappings`). La emisión quedó gateada en el propio IdP, y
está verificado a dos niveles:

| Verificación | Dónde | Cuándo corre |
|---|---|---|
| `KeycloakAuthIT#keycloakGatesScopeEscalation` | Testcontainers con Keycloak real | Cada PR — check **obligatorio** `Integration Tests (Testcontainers)` |
| Paso "G-8 — scope emission gated by role" | `staging.yml`, contra el realm desplegado | Cada despliegue |

Ambos comprueban lo mismo: un `viewer` que pide scopes elevados recibe en el claim `scope` solo
`product:view`. La premisa de ADR-002 dejó de ser cierta.

---

## Decisión

**El backend confía en el claim `scope` del token tal cual.** Se retiran `SCOPES_BY_ROLE`,
`permittedScopesForRoles` y `BASE_SCOPES`. `extractAuthorities` se limita a mapear los roles de
realm a `ROLE_*` y cada scope del token a `SCOPE_*`.

La misma decisión se aplica al frontend: `frontend/src/lib/scopes.ts` mantenía un espejo del mapa
(G-3a) y `AuthContext` filtraba los scopes del token contra él. Retirado también; `AuthContext` lee
los scopes del token directamente.

La autorización efectiva sigue siendo por permiso y no por nombre de rol: los `@PreAuthorize`
comprueban `SCOPE_*`. Los `ROLE_*` se conservan porque `MeController` los expone al frontend para
mostrar el rol del usuario; **no autorizan nada**, y ahora eso es estructural, no una convención.

---

## Por qué retirar el mapa y no conservarlo como defensa en profundidad

Es la alternativa que más se consideró, y se descartó por tres razones.

### 1. Las dos capas no fallan de forma independiente

El mapa Java protegía contra **un solo** fallo: que un scope quedara marcado como opcional sin su
`scope-mapping`. No protegía contra ningún otro. Si alguien asigna por error el rol
`inventory-admin` a un viewer, el mapa le concede alegremente los siete scopes, porque su clave de
entrada es el rol —que sale del **mismo** realm que se quería vigilar—. Dos controles que derivan
de la misma fuente y fallan a la vez no son dos capas.

### 2. El único fallo que cubría ya lo cubre un test, y mejor

En runtime, el mapa **enmascaraba** la mala configuración: el realm quedaba roto y nadie se
enteraba, porque el backend lo tapaba en silencio. Sin el mapa, el mismo error tumba
`KeycloakAuthIT` en el check obligatorio de cada PR, con nombre de test y línea. Fallar
ruidosamente en CI es mejor control que parchear calladamente en producción.

### 3. El híbrido salía más caro que el problema

Se evaluó conservar el mapa añadiendo un test de consistencia realm↔Java que impidiera la
divergencia. No es viable a coste razonable: los `scope-mappings` **no viven en**
`keycloak/realm-export.json` —ese fichero solo trae realm, roles y clients—, sino en
`scripts/keycloak/init-users.sh`, un script de shell. El test tendría que parsear shell desde Java.
Añadir un tercer artefacto que mantener sincronizado, para no quitar el segundo.

Y de fondo: un resource server que vuelve a derivar la autorización a partir de los roles está
reimplementando el IdP. El modelo que pide el enunciado —Keycloak, OAuth2, JWT, roles, permisos,
scopes— sitúa la autoridad en Keycloak.

---

## Alternativas consideradas

| Alternativa | Razón de descarte |
|---|---|
| **Conservar el mapa como defensa en profundidad** | Las tres razones de arriba. La protección era estrecha, redundante con un test, y enmascaraba el fallo en vez de exponerlo |
| **Conservar el mapa + test de consistencia realm↔Java** | Los `scope-mappings` viven en un script de shell, no en el realm export. El test tendría que parsear shell; frágil y con un artefacto más que sincronizar |
| **Volver a ADR-002 (statu quo)** | La premisa que lo justificaba (G-6) dejó de ser cierta con G-8. Mantener una contención cuya causa se corrigió es deuda, no prudencia |
| **Keycloak Authorization Services (Resources, Policies, Permissions)** | Es **G-1**, decisión aparte y todavía abierta. No bloquea esta: G-1 añadiría una capa de políticas por encima, no cambia quién es la autoridad sobre el claim `scope` |

---

## Consecuencias

### Positivas

- **Una sola fuente de verdad.** El mapa rol→scopes vive solo en el realm. Antes estaba en tres
  sitios —realm, `SecurityConfig.java` y `frontend/src/lib/scopes.ts`— y los tres podían divergir.
- **Menos código y menos superficie de error.** Desaparecen ~45 líneas de backend y el mapa del
  cliente, junto con el riesgo de que alguien edite uno y olvide los otros dos.
- **Alta de un rol sin tocar código.** Añadir un rol al realm con sus `scope-mappings` basta;
  antes exigía además un despliegue del backend y otro del frontend, y sin ellos el usuario
  autenticaba pero no veía nada, sin ningún error que lo explicara.
- **El fallo se hace visible.** Un realm mal configurado ahora rompe un check obligatorio en vez de
  quedar tapado.

### Negativas / Riesgos

- **El realm pasa a ser el único control de acceso.** Es la consecuencia central y hay que
  asumirla conscientemente. Se mitiga con `KeycloakAuthIT#keycloakGatesScopeEscalation`, dentro del
  check obligatorio `Integration Tests (Testcontainers)`: ese test es la red y **no debe
  relajarse ni marcarse como opcional**. Si alguien lo salta, se vuelve a G-6 sin nada debajo.
- **Cambio de comportamiento en un caso límite.** Un token sin roles de realm ya no recibe cero
  autoridades: recibe las de sus scopes OIDC (`openid`, `profile`, `email`). Es inocuo —ningún
  endpoint autoriza sobre ellos—, pero el test que fijaba el comportamiento anterior
  (`noRealmRoles_grantsNoScopes`) se reescribió como `noRealmRoles_grantsOnlyTokenScopes`.
- **La configuración del realm gana peso como artefacto crítico.** `init-users.sh` deja de ser
  utilería de arranque y pasa a ser código de seguridad. Cualquier cambio en sus
  `assign_scope_roles` merece la misma revisión que un cambio en `SecurityConfig.java`.

---

## Referencias

- [ADR-002 — sustituido por este](ADR-002-mapa-rol-scopes-en-java.md)
- [Informe G-6 — Escalada de scopes](../testing/reportes/G-6-escalada-de-scopes.md)
- [Informe G-4/G-5 — Scopes por rol](../testing/reportes/G-4-G-5-scopes-por-rol.md)
- Issue #43 — Keycloak emite cualquier scope a cualquier usuario autenticado
- `scripts/keycloak/init-users.sh` — `assign_scope_roles`, la autoridad efectiva
- `SecurityConfig.java` — `extractAuthorities`
- `KeycloakAuthIT#keycloakGatesScopeEscalation` — la red de seguridad
- [RNF-02 — Autorización por permiso, no por rol](../requisitos/requisitos-no-funcionales.md#rnf-02--autorización-por-permiso-no-por-rol)
