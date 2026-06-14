import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import type { AuditRevisionResponse } from '@/types/index'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'
import { Shield } from 'lucide-react'

const REV_BADGE: Record<string, 'blue' | 'green' | 'red'> = {
  ADD: 'green',
  MOD: 'blue',
  DEL: 'red',
}
const REV_LABEL: Record<string, string> = { ADD: 'Creado', MOD: 'Modificado', DEL: 'Eliminado' }

export function AuditPage() {
  const [productId, setProductId] = useState('')
  const [username, setUsername] = useState('')

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['audit', productId, username],
    queryFn: () => {
      const params = new URLSearchParams()
      if (productId) params.set('productId', productId)
      if (username) params.set('username', username)
      return api
        .get<AuditRevisionResponse[]>(`/api/audit/stock-movements?${params}`)
        .then((r) => r.data)
    },
  })

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2 text-gray-500">
          <Shield className="h-4 w-4" />
          <span className="text-sm font-medium text-gray-700">Auditoría Envers</span>
        </div>
        <input
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          placeholder="ID de producto..."
          type="number"
          className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none w-40"
        />
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Usuario..."
          className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none w-44"
        />
        <button
          onClick={() => refetch()}
          className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Filtrar
        </button>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonTable rows={8} />
          </div>
        ) : isError ? (
          <div className="py-16 text-center text-sm text-red-500">
            Error cargando auditoría
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50">
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Rev #</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Acción</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Producto</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Tipo mov.</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Cantidad</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Antes → Después</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Usuario</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Fecha</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.map((rev) => (
                <tr key={`${rev.revisionNumber}-${rev.movementId}`} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-400">#{rev.revisionNumber}</td>
                  <td className="px-4 py-3">
                    <Badge variant={REV_BADGE[rev.revisionType] ?? 'gray'}>
                      {REV_LABEL[rev.revisionType] ?? rev.revisionType}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">{rev.productName}</div>
                    <div className="text-xs text-gray-400">{rev.sku}</div>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{rev.movementType}</td>
                  <td className="px-4 py-3 text-right font-semibold">{rev.quantity}</td>
                  <td className="px-4 py-3 text-right text-xs text-gray-500">
                    {rev.quantityBefore} → {rev.quantityAfter}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{rev.revisedBy}</td>
                  <td className="px-4 py-3 text-xs text-gray-400">
                    {new Date(rev.revisionTimestamp).toLocaleString('es-DO', {
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
                  <td colSpan={8} className="py-16 text-center text-sm text-gray-400">
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
