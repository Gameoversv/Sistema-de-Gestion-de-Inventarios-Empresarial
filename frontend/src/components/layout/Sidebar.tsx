/** Side navigation bar listing application sections, with each link conditionally shown based on the user's permission scopes. */
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Package,
  ArrowLeftRight,
  ClipboardList,
  BarChart3,
  LogOut,
  BoxIcon,
} from 'lucide-react'
import { useAuth } from '@/contexts/AuthContext'
import { PermissionGuard } from '@/components/auth/PermissionGuard'

const NAV = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard', scope: 'report:view' },
  { to: '/products', icon: Package, label: 'Productos', scope: 'product:view' },
  { to: '/stock', icon: ArrowLeftRight, label: 'Stock', scope: 'stock:view' },
  { to: '/reports', icon: BarChart3, label: 'Reportes', scope: 'report:view' },
  { to: '/audit', icon: ClipboardList, label: 'Auditoría', scope: 'audit:view' },
]

export function Sidebar() {
  const { logout, username } = useAuth()

  return (
    <aside className="flex h-screen w-60 flex-col bg-gray-900 border-r border-gray-800">
      {/* Logo */}
      <div className="flex items-center gap-2 px-5 py-5 border-b border-gray-800">
        <BoxIcon className="h-7 w-7 text-indigo-400" />
        <span className="font-semibold text-white text-sm leading-tight">
          Inventario<br />
          <span className="text-indigo-400 text-xs font-normal">Empresarial</span>
        </span>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {NAV.map(({ to, icon: Icon, label, scope }) => (
          <PermissionGuard key={to} scope={scope}>
            <NavLink
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-indigo-600 text-white'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-white'
                }`
              }
            >
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </NavLink>
          </PermissionGuard>
        ))}
      </nav>

      {/* User */}
      <div className="border-t border-gray-800 px-4 py-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-white">{username ?? 'Usuario'}</p>
            <p className="text-xs text-gray-500">Keycloak</p>
          </div>
          <button
            onClick={logout}
            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-800 hover:text-red-400 transition-colors"
            title="Cerrar sesión"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </aside>
  )
}
