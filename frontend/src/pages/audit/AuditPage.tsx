/** Audit log page that shows a paginated, searchable history of entity changes with revision badges. */
import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import type { UnifiedAuditEntry } from '@/types/index'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'
import { Shield, RefreshCw, Lock } from 'lucide-react'
import { useAuth } from '@/contexts/AuthContext'

const REV_BADGE: Record<string, 'blue' | 'green' | 'red'> = {
  ADD: 'green',
  MOD: 'blue',
  DEL: 'red',
}
const REV_LABEL: Record<string, string> = { ADD: 'Creado', MOD: 'Modificado', DEL: 'Eliminado' }

const ENTITY_LABEL: Record<string, string> = {
  PRODUCT: 'Producto',
  CATEGORY: 'Categoría',
  STOCK_MOVEMENT: 'Movimiento',
  USER: 'Usuario',
}

const ENTITY_BADGE: Record<string, 'blue' | 'green' | 'red' | 'gray'> = {
  PRODUCT: 'blue',
  CATEGORY: 'green',
  STOCK_MOVEMENT: 'gray',
  USER: 'red',
}

export function AuditPage() {
  const { hasScope } = useAuth()
  const canView = hasScope('audit:view')

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['audit-all'],
    queryFn: () =>
      api.get<UnifiedAuditEntry[]>('/api/audit/all').then((r) => r.data),
    staleTime: 0,
    refetchOnWindowFocus: true,
    enabled: canView,
  })

  if (!canView) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3 text-gray-400">
        <Lock className="h-8 w-8" />
        <p className="text-sm font-medium">Sin permiso para ver auditoría</p>
        <p className="text-xs">Requiere rol auditor o administrador</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-gray-500">
          <Shield className="h-4 w-4" />
          <span className="text-sm font-medium text-gray-700">Auditoría Envers — Historial Completo</span>
        </div>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          Actualizar
        </button>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
        {isLoading ? (
          <div className="p-4"><SkeletonTable rows={8} /></div>
        ) : isError ? (
          <div className="py-16 text-center text-sm text-red-500">
            Error cargando auditoría
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50">
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Rev #</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entidad</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Acción</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Detalle</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Usuario</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Fecha</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.map((entry) => (
                <tr key={`${entry.revisionNumber}-${entry.entityType}-${entry.entityId}`} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-400">#{entry.revisionNumber}</td>
                  <td className="px-4 py-3">
                    <Badge variant={ENTITY_BADGE[entry.entityType] ?? 'gray'}>
                      {ENTITY_LABEL[entry.entityType] ?? entry.entityType}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={REV_BADGE[entry.revisionType] ?? 'gray'}>
                      {REV_LABEL[entry.revisionType] ?? entry.revisionType}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-700 max-w-xs truncate">{entry.summary}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{entry.revisedBy}</td>
                  <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                    {new Date(entry.revisionTimestamp).toLocaleString('es-DO', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </td>
                </tr>
              ))}
              {data?.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-16 text-center text-sm text-gray-400">
                    Sin registros de auditoría
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
