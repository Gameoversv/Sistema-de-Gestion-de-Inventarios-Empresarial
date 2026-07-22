/** Shell layout that wraps every page with the sidebar navigation and the contextual page header. */
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'

const TITLES: Record<string, string> = {
  '/': 'Dashboard',
  '/products': 'Productos',
  '/stock': 'Control de Stock',
  '/reports': 'Reportes',
  '/audit': 'Auditoría',
}

export function Layout() {
  const { pathname } = useLocation()
  const title = TITLES[pathname] ?? 'Inventario'

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header title={title} />
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
