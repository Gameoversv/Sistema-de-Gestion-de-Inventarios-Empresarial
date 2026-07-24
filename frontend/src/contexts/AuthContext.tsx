/** Authentication context that exposes Keycloak login state, user roles, permission scopes, and a logout action to the component tree. */
import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import keycloak from '@/lib/keycloak'
import { permittedScopesForRoles, LOGIN_SCOPE } from '@/lib/scopes'

interface AuthContextValue {
  authenticated: boolean
  token: string | undefined
  username: string | undefined
  roles: string[]
  scopes: string[]
  hasScope: (scope: string) => boolean
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

// Init once at module level — outside React lifecycle, immune to StrictMode double-run
const _initPromise: Promise<boolean> = keycloak
  .init({
    onLoad: 'check-sso',
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    checkLoginIframe: false,
    pkceMethod: 'S256',
  })
  .then((authenticated) => {
    if (!authenticated) {
      keycloak.login({ scope: LOGIN_SCOPE })
      return new Promise<boolean>(() => {})
    }
    return true
  })

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    _initPromise.then(() => setReady(true)).catch(() => setReady(true))
  }, [])

  if (!ready) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
          <p className="text-sm text-gray-400">Conectando...</p>
        </div>
      </div>
    )
  }

  const realmRoles: string[] =
    (keycloak.tokenParsed?.realm_access as { roles?: string[] } | undefined)?.roles ?? []

  const tokenScopes: string[] =
    (keycloak.tokenParsed?.scope as string | undefined)?.split(' ').filter(Boolean) ?? []

  const permitted = permittedScopesForRoles(realmRoles)
  const scopes = tokenScopes.filter((s) => permitted.has(s))

  const value: AuthContextValue = {
    authenticated: keycloak.authenticated ?? false,
    token: keycloak.token,
    username: keycloak.tokenParsed?.preferred_username as string | undefined,
    roles: realmRoles,
    scopes,
    hasScope: (scope: string) => scopes.includes(scope),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
