import { useQuery } from '@tanstack/react-query'
import {
  Package,
  TrendingDown,
  AlertTriangle,
  DollarSign,
  BarChart3,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
} from 'lucide-react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts'
import api from '@/lib/api'
import type { DashboardMetricsResponse, TopProductsResponse, RecentMovementsResponse } from '@/types/index'
import { SkeletonCard } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'

function KpiCard({
  title,
  value,
  sub,
  icon: Icon,
  color,
}: {
  title: string
  value: string | number
  sub?: string
  icon: React.ElementType
  color: string
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-gray-500">{title}</p>
        <div className={`rounded-lg p-2 ${color}`}>
          <Icon className="h-4 w-4 text-white" />
        </div>
      </div>
      <p className="mt-3 text-2xl font-bold text-gray-900">{value}</p>
      {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
    </div>
  )
}

const MOVEMENT_ICON: Record<string, React.ElementType> = {
  IN: ArrowUpRight,
  OUT: ArrowDownRight,
  ADJUSTMENT: Minus,
}
const MOVEMENT_COLOR: Record<string, string> = {
  IN: 'text-green-600',
  OUT: 'text-red-600',
  ADJUSTMENT: 'text-amber-600',
}

export function DashboardPage() {
  const { data: metrics, isLoading: loadingMetrics } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: () => api.get<DashboardMetricsResponse>('/api/reports/dashboard-metrics').then((r) => r.data),
    staleTime: 30_000,
  })

  const { data: topProducts } = useQuery({
    queryKey: ['top-products'],
    queryFn: () =>
      api.get<TopProductsResponse>('/api/reports/top-products?limit=10&metric=value').then((r) => r.data),
    staleTime: 60_000,
  })

  const { data: recentMovements } = useQuery({
    queryKey: ['recent-movements'],
    queryFn: () =>
      api.get<RecentMovementsResponse>('/api/reports/recent-movements?limit=10').then((r) => r.data),
    staleTime: 30_000,
  })

  const chartData = topProducts?.products.slice(0, 8).map((p) => ({
    name: p.name.length > 18 ? p.name.slice(0, 18) + '…' : p.name,
    valor: Number(p.inventoryValue.toFixed(0)),
    stock: p.currentStock,
  })) ?? []

  return (
    <div className="space-y-6">
      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {loadingMetrics ? (
          Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)
        ) : (
          <>
            <KpiCard
              title="Productos activos"
              value={metrics?.activeProducts ?? 0}
              sub={`${metrics?.totalProducts ?? 0} total`}
              icon={Package}
              color="bg-indigo-500"
            />
            <KpiCard
              title="Valor inventario"
              value={`$${Number(metrics?.totalInventoryValue ?? 0).toLocaleString('es-DO', { maximumFractionDigits: 0 })}`}
              sub="precio × stock"
              icon={DollarSign}
              color="bg-green-500"
            />
            <KpiCard
              title="Stock bajo"
              value={metrics?.lowStockCount ?? 0}
              sub="bajo mínimo"
              icon={TrendingDown}
              color="bg-amber-500"
            />
            <KpiCard
              title="Stock crítico"
              value={metrics?.criticalStockCount ?? 0}
              sub="stock = 0"
              icon={AlertTriangle}
              color="bg-red-500"
            />
          </>
        )}
      </div>

      {/* Chart + Movements */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        {/* Top products chart */}
        <div className="lg:col-span-3 rounded-xl border border-gray-200 bg-white p-5">
          <div className="flex items-center gap-2 mb-4">
            <BarChart3 className="h-4 w-4 text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-900">Top 8 — valor de inventario</h3>
          </div>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={chartData} margin={{ top: 0, right: 0, left: -20, bottom: 40 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis
                  dataKey="name"
                  tick={{ fontSize: 10, fill: '#9ca3af' }}
                  angle={-35}
                  textAnchor="end"
                  interval={0}
                />
                <YAxis tick={{ fontSize: 10, fill: '#9ca3af' }} />
                <Tooltip
                  formatter={(v) => [`$${Number(v).toLocaleString()}`, 'Valor']}
                  contentStyle={{ fontSize: 12, borderRadius: 8 }}
                />
                <Bar dataKey="valor" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-60 flex items-center justify-center text-sm text-gray-400">
              Sin datos de productos
            </div>
          )}
        </div>

        {/* Recent movements */}
        <div className="lg:col-span-2 rounded-xl border border-gray-200 bg-white p-5">
          <h3 className="mb-4 text-sm font-semibold text-gray-900">Movimientos recientes</h3>
          {recentMovements?.movements.length === 0 ? (
            <p className="text-sm text-gray-400">Sin movimientos</p>
          ) : (
            <div className="space-y-3">
              {recentMovements?.movements.map((m) => {
                const Icon = MOVEMENT_ICON[m.type] ?? Minus
                return (
                  <div key={m.id} className="flex items-start gap-3">
                    <div className={`mt-0.5 ${MOVEMENT_COLOR[m.type]}`}>
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-gray-900 truncate">{m.productName}</p>
                      <p className="text-xs text-gray-400">
                        {m.type} · {m.quantity} u · {m.performedBy}
                      </p>
                    </div>
                    <Badge
                      variant={m.type === 'IN' ? 'green' : m.type === 'OUT' ? 'red' : 'yellow'}
                    >
                      {m.quantity}
                    </Badge>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
