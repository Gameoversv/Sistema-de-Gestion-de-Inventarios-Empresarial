import { type ReactNode } from 'react'
import { useAuth } from '@/contexts/AuthContext'

interface Props {
  scope: string
  fallback?: ReactNode
  children: ReactNode
}

export function PermissionGuard({ scope, fallback, children }: Props) {
  const { hasScope } = useAuth()
  if (!hasScope(scope)) {
    return fallback ? <>{fallback}</> : null
  }
  return <>{children}</>
}
