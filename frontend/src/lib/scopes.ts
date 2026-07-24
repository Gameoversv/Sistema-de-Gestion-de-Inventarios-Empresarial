/**
 * Techo de scopes por rol de realm en el cliente. Espeja SCOPES_BY_ROLE en
 * SecurityConfig.java — mantener en sincronía.
 *
 * El backend es la autoridad real (ADR-002); esto solo decide qué muestra la
 * interfaz. Aun así debe calcularse igual, o el usuario ve controles que el
 * backend le negaría, o al revés.
 *
 * Vive en su propio módulo, sin efectos, para poder probarse sin arrastrar la
 * inicialización de Keycloak que hace AuthContext a nivel de módulo.
 */
const SCOPES_BY_ROLE: Record<string, string[]> = {
  'inventory-admin': [
    'product:view', 'product:manage', 'stock:view', 'stock:manage',
    'report:view', 'user:manage', 'audit:view',
  ],
  'warehouse-clerk': [
    'product:view', 'product:manage', 'stock:view', 'stock:manage', 'report:view',
  ],
  auditor: ['product:view', 'stock:view', 'report:view', 'audit:view'],
  viewer: ['product:view', 'stock:view', 'report:view'],
}

/**
 * Scopes de negocio que el frontend solicita en el login. Son *optional client scopes* en
 * Keycloak: si no se piden, no llegan al token y `PermissionGuard` oculta toda la interfaz
 * protegida. Se piden los siete; los `scope-mappings` del realm (G-8) recortan por rol, así que
 * cada usuario recibe solo los que su rol permite. `openid` lo añade keycloak-js por su cuenta.
 */
export const LOGIN_SCOPE =
  'product:view product:manage stock:view stock:manage report:view audit:view'

/**
 * Devuelve la UNIÓN de los scopes de todos los roles reconocidos, igual que
 * permittedScopesForRoles() en el backend. Un rol no reconocido no aporta nada;
 * sin ningún rol reconocido, el conjunto queda vacío: denegar por defecto.
 *
 * Antes tomaba el primer rol coincidente (bug G-3a): un usuario con varios roles
 * veía en la interfaz menos de lo que el backend le concedía.
 */
export function permittedScopesForRoles(roles: string[]): Set<string> {
  const permitted = new Set<string>()
  for (const role of roles) {
    for (const scope of SCOPES_BY_ROLE[role] ?? []) {
      permitted.add(scope)
    }
  }
  return permitted
}
