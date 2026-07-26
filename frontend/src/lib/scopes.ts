/**
 * Scopes de negocio que el frontend solicita en el login. Son *optional client scopes* en
 * Keycloak: si no se piden, no llegan al token y `PermissionGuard` oculta toda la interfaz
 * protegida. Se piden todos; los `scope-mappings` del realm (G-8) recortan por rol, así que
 * cada usuario recibe solo los que su rol permite. `openid` lo añade keycloak-js por su cuenta.
 *
 * `user:manage` se incluye desde que existe el módulo de gestión de usuarios. Estuvo fuera
 * mientras el permiso no protegía ningún endpoint: pedirlo entonces solo añadía ruido a la
 * pantalla de consentimiento. Ahora sin él la sección de Usuarios queda invisible incluso para
 * el administrador, porque `PermissionGuard` mira el scope del token y no el rol.
 *
 * Este módulo llegó a tener también un mapa rol→scopes que espejaba el del backend. Se retiró en
 * G-2: el token ya llega recortado por rol desde Keycloak, así que recalcular el techo aquí solo
 * podía introducir divergencia con el backend. Ver
 * `docs/decisions/ADR-004-keycloak-autoridad-de-scopes.md`.
 */
export const LOGIN_SCOPE =
  'product:view product:manage stock:view stock:manage report:view audit:view user:manage'
