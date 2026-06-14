import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowUpRight, ArrowDownRight, Minus, ChevronLeft, ChevronRight, AlertCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '@/lib/api'
import type { StockMovementResponse, StockMovementRequest, ProductResponse, Page } from '@/types/index'
import { PermissionGuard } from '@/components/auth/PermissionGuard'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'

// ── Hooks ────────────────────────────────────────────────
function useMovements(page: number) {
  return useQuery({
    queryKey: ['stock-movements', page],
    queryFn: () =>
      api
        .get<Page<StockMovementResponse>>(`/api/stock/movements?sort=createdAt,desc&page=${page}&size=20`)
        .then((r) => r.data),
  })
}

function useLowStockAlerts() {
  return useQuery({
    queryKey: ['stock-alerts'],
    queryFn: () => api.get<ProductResponse[]>('/api/stock/alerts').then((r) => r.data),
    staleTime: 30_000,
  })
}

function useAllProducts() {
  return useQuery({
    queryKey: ['products-all'],
    queryFn: () =>
      api.get<Page<ProductResponse>>('/products?size=200&sort=name&active=true').then((r) => r.data.content),
    staleTime: 60_000,
  })
}

// ── Schema ───────────────────────────────────────────────
const movementSchema = z.object({
  productId: z.string().min(1, 'Producto requerido'),
  type: z.enum(['IN', 'OUT', 'ADJUSTMENT']),
  quantity: z.string().min(1),
  reason: z.string().optional(),
  referenceId: z.string().optional(),
})

type FormData = z.infer<typeof movementSchema>

// ── Type helpers ─────────────────────────────────────────
const TYPE_LABEL: Record<string, string> = { IN: 'Entrada', OUT: 'Salida', ADJUSTMENT: 'Ajuste' }
const TYPE_BADGE: Record<string, 'green' | 'red' | 'yellow'> = {
  IN: 'green',
  OUT: 'red',
  ADJUSTMENT: 'yellow',
}
const TYPE_ICON: Record<string, React.ElementType> = {
  IN: ArrowUpRight,
  OUT: ArrowDownRight,
  ADJUSTMENT: Minus,
}

// ── RegisterMovementForm ─────────────────────────────────
function RegisterMovementForm() {
  const qc = useQueryClient()
  const { data: products = [] } = useAllProducts()

  const mutation = useMutation({
    mutationFn: (body: StockMovementRequest) =>
      api.post<StockMovementResponse>('/api/stock/movements', body).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['stock-movements'] })
      qc.invalidateQueries({ queryKey: ['stock-alerts'] })
      qc.invalidateQueries({ queryKey: ['dashboard-metrics'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      toast.success('Movimiento registrado')
      reset()
    },
    onError: () => toast.error('Error al registrar movimiento'),
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(movementSchema),
    defaultValues: { type: 'IN', quantity: '1' },
  })

  const onSubmit = (data: FormData) =>
    mutation.mutate({
      productId: Number(data.productId),
      type: data.type,
      quantity: Number(data.quantity),
      reason: data.reason,
      referenceId: data.referenceId,
    })

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5">
      <h3 className="mb-4 text-sm font-semibold text-gray-900">Registrar movimiento</h3>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="text-xs font-medium text-gray-700">Producto *</label>
            <select
              {...register('productId')}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
            >
              <option value="">Seleccionar...</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.sku} — {p.name} (stock: {p.stock})
                </option>
              ))}
            </select>
            {errors.productId && (
              <p className="mt-1 text-xs text-red-600">{errors.productId.message}</p>
            )}
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Tipo *</label>
            <select
              {...register('type')}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
            >
              <option value="IN">Entrada</option>
              <option value="OUT">Salida</option>
              <option value="ADJUSTMENT">Ajuste</option>
            </select>
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Cantidad *</label>
            <input
              {...register('quantity')}
              type="number"
              min="1"
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
            />
            {errors.quantity && (
              <p className="mt-1 text-xs text-red-600">{errors.quantity.message}</p>
            )}
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Motivo</label>
            <input
              {...register('reason')}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
              placeholder="Reposición mensual..."
            />
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Referencia</label>
            <input
              {...register('referenceId')}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
              placeholder="PO-2024-001"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting || mutation.isPending}
          className="w-full rounded-lg bg-indigo-600 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {mutation.isPending ? 'Registrando...' : 'Registrar movimiento'}
        </button>
      </form>
    </div>
  )
}

// ── AlertsPanel ──────────────────────────────────────────
function AlertsPanel() {
  const { data: alerts = [] } = useLowStockAlerts()

  if (alerts.length === 0) return null

  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
      <div className="flex items-center gap-2 mb-3">
        <AlertCircle className="h-4 w-4 text-amber-600" />
        <h3 className="text-sm font-semibold text-amber-800">
          {alerts.length} producto{alerts.length > 1 ? 's' : ''} bajo stock mínimo
        </h3>
      </div>
      <div className="space-y-1.5">
        {alerts.slice(0, 5).map((p) => (
          <div key={p.id} className="flex items-center justify-between text-xs text-amber-700">
            <span className="font-medium">{p.name}</span>
            <span>
              {p.stock} / {p.minimumStock} mín
            </span>
          </div>
        ))}
        {alerts.length > 5 && (
          <p className="text-xs text-amber-600">+{alerts.length - 5} más...</p>
        )}
      </div>
    </div>
  )
}

// ── StockPage ────────────────────────────────────────────
export function StockPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMovements(page)

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      {/* Left: form + alerts */}
      <div className="space-y-4">
        <PermissionGuard scope="stock:manage">
          <RegisterMovementForm />
        </PermissionGuard>
        <AlertsPanel />
      </div>

      {/* Right: movements table */}
      <div className="lg:col-span-2 space-y-4">
        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
          <div className="border-b border-gray-200 px-5 py-3">
            <h3 className="text-sm font-semibold text-gray-900">Historial de movimientos</h3>
          </div>
          {isLoading ? (
            <div className="p-4">
              <SkeletonTable rows={8} />
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50">
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Tipo</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Producto</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Cantidad</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Antes → Después</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Usuario</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Fecha</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data?.content?.map((m) => {
                  const Icon = TYPE_ICON[m.type] ?? Minus
                  return (
                    <tr key={m.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          <Icon
                            className={`h-3.5 w-3.5 ${
                              m.type === 'IN'
                                ? 'text-green-600'
                                : m.type === 'OUT'
                                ? 'text-red-600'
                                : 'text-amber-600'
                            }`}
                          />
                          <Badge variant={TYPE_BADGE[m.type]}>{TYPE_LABEL[m.type]}</Badge>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-gray-900">{m.productName}</div>
                        <div className="text-xs text-gray-400">{m.sku}</div>
                      </td>
                      <td className="px-4 py-3 text-right font-semibold text-gray-900">{m.quantity}</td>
                      <td className="px-4 py-3 text-right text-xs text-gray-500">
                        {m.quantityBefore} → {m.quantityAfter}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-600">{m.performedBy}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">
                        {new Date(m.createdAt).toLocaleString('es-DO', {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </td>
                    </tr>
                  )
                })}
                {data?.content?.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-16 text-center text-sm text-gray-400">
                      Sin movimientos registrados
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between text-sm text-gray-600">
            <span>
              {data.totalElements} movimientos · Página {data.number + 1} de {data.totalPages}
            </span>
            <div className="flex gap-1">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded-md border border-gray-300 p-1.5 hover:bg-gray-50 disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= data.totalPages - 1}
                className="rounded-md border border-gray-300 p-1.5 hover:bg-gray-50 disabled:opacity-40"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
