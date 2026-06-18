import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { useAuth } from '@/contexts/AuthContext'
import { PermissionGuard } from '@/components/auth/PermissionGuard'

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: vi.fn(),
}))

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

describe('PermissionGuard', () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({
      hasScope: (s: string) => s === 'product:view',
    })
  })

  // Verifica que PermissionGuard muestra los hijos cuando el scope del usuario coincide.
  it('renders children when the scope matches', () => {
    render(
      <PermissionGuard scope="product:view">
        <span>Protected content</span>
      </PermissionGuard>,
    )
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  // Verifica que PermissionGuard no renderiza nada cuando el scope no coincide con el del usuario.
  it('renders nothing when the scope does not match', () => {
    render(
      <PermissionGuard scope="product:delete">
        <span>Admin content</span>
      </PermissionGuard>,
    )
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument()
  })
})
