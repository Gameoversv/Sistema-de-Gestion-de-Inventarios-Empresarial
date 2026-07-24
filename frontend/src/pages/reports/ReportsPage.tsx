/** Reports page presenting inventory analytics through bar charts, pie charts, and low-stock and top-movement tables. */
import { useQuery } from '@tanstack/react-query'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import { TrendingDown, AlertTriangle, BarChart3, Package } from 'lucide-react'
import api from '@/lib/api'
import type { LowStockReportResponse, CriticalStockResponse, TopProductsResponse } from '@/types/index'
import { Badge } from '@/components/ui/Badge'

const COLORS = [
  '#6366f1',
  '#8b5cf6',
  '#06b6d4',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#ec4899',
  '#14b8a6',
]

export function ReportsPage() {
  const { data: lowStock, isLoading: loadingLow } = useQuery({
    queryKey: ['report-low-stock'],
    queryFn: () => api.get<LowStockReportResponse>('/api/reports/low-stock').then((r) => r.data),
  })

  const { data: criticalStock, isLoading: loadingCritical } = useQuery({
    queryKey: ['report-critical'],
    queryFn: () => api.get<CriticalStockResponse>('/api/reports/critical-stock').then((r) => r.data),
  })

  const { data: topByValue } = useQuery({
    queryKey: ['report-top-value'],
    queryFn: () =>
      api
        .get<TopProductsResponse>('/api/reports/top-products?limit=10&metric=value')
        .then((r) => r.data),
  })

  const { data: topByQty } = useQuery({
    queryKey: ['report-top-qty'],
    queryFn: () =>
      api
        .get<TopProductsResponse>('/api/reports/top-products?limit=10&metric=quantity')
        .then((r) => r.data),
  })

  const barData =
    topByValue?.products?.slice(0, 8).map((p) => ({
      name: p.name.length > 16 ? p.name.slice(0, 16) + '…' : p.name,
      valor: Number(p.inventoryValue.toFixed(0)),
    })) ?? []

  const pieData =
    topByQty?.products?.slice(0, 8).map((p, i) => ({
      name: p.name.length > 16 ? p.name.slice(0, 16) + '…' : p.name,
      value: p.currentStock,
      fill: COLORS[i % COLORS.length],
    })) ?? []

  return (
    <div className="space-y-6">
      {/* KPI row */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
          <div className="flex items-center gap-2 mb-2">
            <TrendingDown className="h-4 w-4 text-amber-600" />
            <p className="text-xs font-medium text-amber-700">Stock bajo</p>
          </div>
          {loadingLow ? (
            <div className="h-8 w-12 animate-pulse rounded bg-amber-200" />
          ) : (
            <p className="text-2xl font-bold text-amber-800">{lowStock?.count ?? 0}</p>
          )}
          <p className="text-xs text-amber-600 mt-1">productos bajo mínimo</p>
        </div>

        <div className="rounded-xl border border-red-200 bg-red-50 p-4">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle className="h-4 w-4 text-red-600" />
            <p className="text-xs font-medium text-red-700">Stock crítico</p>
          </div>
          {loadingCritical ? (
            <div className="h-8 w-12 animate-pulse rounded bg-red-200" />
          ) : (
            <p className="text-2xl font-bold text-red-800">{criticalStock?.count ?? 0}</p>
          )}
          <p className="text-xs text-red-600 mt-1">con stock = 0</p>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Bar: top by value */}
        <div className="rounded-xl border border-gray-200 bg-white p-5">
          <div className="flex items-center gap-2 mb-4">
            <BarChart3 className="h-4 w-4 text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-900">Top 8 por valor ($precio×stock)</h3>
          </div>
          {barData.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={barData} margin={{ top: 0, right: 0, left: -20, bottom: 48 }}>
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
              Sin datos
            </div>
          )}
        </div>

        {/* Pie: top by qty */}
        <div className="rounded-xl border border-gray-200 bg-white p-5">
          <div className="flex items-center gap-2 mb-4">
            <Package className="h-4 w-4 text-gray-400" />
            <h3 className="text-sm font-semibold text-gray-900">Top 8 por cantidad en stock</h3>
          </div>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" outerRadius={80} dataKey="value" nameKey="name">
                  {pieData.map((entry, index) => (
                    <Cell key={index} fill={entry.fill} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(v, name) => [v, name]}
                  contentStyle={{ fontSize: 12, borderRadius: 8 }}
                />
                <Legend formatter={(v) => <span style={{ fontSize: 11 }}>{v}</span>} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-60 flex items-center justify-center text-sm text-gray-400">
              Sin datos
            </div>
          )}
        </div>
      </div>

      {/* Low stock table */}
      {(lowStock?.items?.length ?? 0) > 0 && (
        <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
          <div className="border-b border-gray-200 px-5 py-3 flex items-center gap-2">
            <TrendingDown className="h-4 w-4 text-amber-500" />
            <h3 className="text-sm font-semibold text-gray-900">
              Detalle — productos bajo stock mínimo
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50">
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">SKU</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Nombre</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Categoría</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                  Stock actual
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Mínimo</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Déficit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {lowStock?.items?.map((item) => (
                <tr key={item.productId} className="hover:bg-amber-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{item.sku}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{item.name}</td>
                  <td className="px-4 py-3 text-gray-600">{item.categoryName}</td>
                  <td className="px-4 py-3 text-right text-red-600 font-semibold">{item.currentStock}</td>
                  <td className="px-4 py-3 text-right text-gray-500">{item.minimumStock}</td>
                  <td className="px-4 py-3 text-right">
                    <Badge variant="red">-{item.deficit}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Critical stock */}
      {(criticalStock?.products?.length ?? 0) > 0 && (
        <div className="rounded-xl border border-red-200 bg-white overflow-hidden">
          <div className="border-b border-red-200 bg-red-50 px-5 py-3 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-red-500" />
            <h3 className="text-sm font-semibold text-red-800">Productos sin stock (crítico)</h3>
          </div>
          <div className="divide-y divide-gray-100">
            {criticalStock?.products?.map((p) => (
              <div key={p.productId} className="flex items-center justify-between px-5 py-3">
                <div>
                  <p className="text-sm font-medium text-gray-900">{p.name}</p>
                  <p className="text-xs text-gray-400">
                    {p.sku} · {p.categoryName}
                  </p>
                </div>
                <Badge variant="red">Stock: 0</Badge>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
