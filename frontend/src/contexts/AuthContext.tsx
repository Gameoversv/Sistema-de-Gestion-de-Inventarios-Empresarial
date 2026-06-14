import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import keycloak from '@/lib/keycloak'

interface AuthContextValue {
  authenticated: boolean
  token: string | undefined
  username: string | undefined
  scopes: string[]
  hasScope: (scope: string) => boolean
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(false)
  const [initialized, setInitialized] = useState(false)

  useEffect(() => {
    keycloak
      .init({ onLoad: 'login-required', checkLoginIframe: false })
      .then((auth) => {
        setAuthenticated(auth)
        setInitialized(true)

        // refresh token every 4 minutes
        setInterval(() => {
          keycloak.updateToken(60).catch(() => keycloak.login())
        }, 240_000)
      })
      .catch(() => {
        setInitialized(true)
      })
  }, [])

  if (!initialized) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
          <p className="text-sm text-gray-400">Conectando...</p>
        </div>
      </div>
    )
  }

  const scopes: string[] = (keycloak.tokenParsed?.scope as string | undefined)
    ?.split(' ')
    .filter(Boolean) ?? []

  const value: AuthContextValue = {
    authenticated,
    token: keycloak.token,
    username: keycloak.tokenParsed?.preferred_username as string | undefined,
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
