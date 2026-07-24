/** Products management page with search, category filtering, pagination, and permission-gated create/edit/delete actions. */
import { useState } from 'react'
import { Plus, Search, Pencil, Trash2, ChevronLeft, ChevronRight, ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react'
import type { ProductResponse } from '@/types/index'
import { useProducts, useCategories } from '@/hooks/useProducts'
import type { ProductSortField, SortDirection } from '@/hooks/useProducts'
import { PermissionGuard } from '@/components/auth/PermissionGuard'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'
import { ProductFormModal } from './ProductFormModal'
import { DeleteConfirmModal } from './DeleteConfirmModal'

export function ProductsPage() {
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [active, setActive] = useState<boolean | undefined>(true)
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [editProduct, setEditProduct] = useState<ProductResponse | null>(null)
  const [deleteProduct, setDeleteProduct] = useState<ProductResponse | null>(null)
  const [sortBy, setSortBy] = useState<ProductSortField>('name')
  const [sortDir, setSortDir] = useState<SortDirection>('asc')

  // Al reordenar hay que volver a la primera pagina: quedarse en la 3 de un orden nuevo muestra
  // un tramo arbitrario del listado y parece que la ordenacion no ha hecho nada.
  const handleSort = (field: ProductSortField) => {
    if (field === sortBy) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortBy(field)
      setSortDir('asc')
    }
    setPage(0)
  }

  const { data, isLoading, isError } = useProducts({
    search,
    categoryId,
    active,
    page,
    size: 20,
    sortBy,
    sortDir,
  })
  const { data: categories = [] } = useCategories()

  const handleSearchChange = (v: string) => {
    setSearch(v)
    setPage(0)
  }

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-48">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <input
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="Buscar por nombre o SKU..."
            className="w-full rounded-lg border border-gray-300 bg-white py-2 pl-9 pr-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <select
          value={categoryId ?? ''}
          onChange={(e) => {
            setCategoryId(e.target.value ? Number(e.target.value) : undefined)
            setPage(0)
          }}
          className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
        >
          <option value="">Todas las categorías</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>

        <select
          value={active == null ? '' : String(active)}
          onChange={(e) => {
            setActive(e.target.value === '' ? undefined : e.target.value === 'true')
            setPage(0)
          }}
          className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
        >
          <option value="true">Activos</option>
          <option value="false">Inactivos</option>
          <option value="">Todos</option>
        </select>

        <PermissionGuard scope="product:manage">
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            <Plus className="h-4 w-4" />
            Nuevo producto
          </button>
        </PermissionGuard>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonTable rows={8} />
          </div>
        ) : isError ? (
          <div className="py-16 text-center text-sm text-red-500">
            Error cargando productos.{' '}
            <button className="underline" onClick={() => window.location.reload()}>
              Reintentar
            </button>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50">
                <SortableHeader field="sku" label="SKU" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <SortableHeader field="name" label="Nombre" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Categoría</th>
                <SortableHeader field="price" label="Precio" align="right" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <SortableHeader field="stock" label="Stock" align="right" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wide">Estado</th>
                <PermissionGuard scope="product:manage">
                  <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wide">Acciones</th>
                </PermissionGuard>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data?.content?.map((p) => (
                <tr key={p.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{p.sku}</td>
                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">{p.name}</div>
                    {p.description && (
                      <div className="text-xs text-gray-400 truncate max-w-xs">{p.description}</div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{p.categoryName}</td>
                  <td className="px-4 py-3 text-right font-medium text-gray-900">
                    ${p.price.toFixed(2)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <span
                      className={p.stock <= p.minimumStock ? 'text-red-600 font-semibold' : 'text-gray-900'}
                    >
                      {p.stock}
                    </span>
                    <span className="text-gray-400 text-xs"> / {p.minimumStock}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <Badge variant={p.active ? 'green' : 'gray'}>
                      {p.active ? 'Activo' : 'Inactivo'}
                    </Badge>
                  </td>
                  <PermissionGuard scope="product:manage">
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-2">
                        <button
                          onClick={() => setEditProduct(p)}
                          className="rounded-md p-1.5 text-gray-400 hover:bg-indigo-50 hover:text-indigo-600"
                          title="Editar"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => setDeleteProduct(p)}
                          className="rounded-md p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600"
                          title="Desactivar"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </PermissionGuard>
                </tr>
              ))}
              {data?.content?.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-16 text-center text-sm text-gray-400">
                    No se encontraron productos
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-gray-600">
          <span>
            {data.totalElements} productos · Página {data.number + 1} de {data.totalPages}
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

      {/* Modals */}
      {showCreate && (
        <ProductFormModal onClose={() => setShowCreate(false)} />
      )}
      {editProduct && (
        <ProductFormModal product={editProduct} onClose={() => setEditProduct(null)} />
      )}
      {deleteProduct && (
        <DeleteConfirmModal product={deleteProduct} onClose={() => setDeleteProduct(null)} />
      )}
    </div>
  )
}

interface SortableHeaderProps {
  field: ProductSortField
  label: string
  sortBy: ProductSortField
  sortDir: SortDirection
  onSort: (field: ProductSortField) => void
  align?: 'left' | 'right'
}

/**
 * Cabecera de columna ordenable.
 *
 * La flecha solo aparece en la columna activa; el resto muestran el icono neutro en gris claro,
 * que anuncia que la columna se puede ordenar sin competir con el indicador real.
 *
 * `aria-sort` va en el `th` porque es ahi donde los lectores de pantalla lo buscan, no en el boton.
 */
function SortableHeader({ field, label, sortBy, sortDir, onSort, align = 'left' }: SortableHeaderProps) {
  const isActive = sortBy === field
  const Icon = isActive ? (sortDir === 'asc' ? ArrowUp : ArrowDown) : ArrowUpDown

  return (
    <th
      aria-sort={isActive ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={`px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wide ${
        align === 'right' ? 'text-right' : 'text-left'
      }`}
    >
      <button
        type="button"
        onClick={() => onSort(field)}
        title={`Ordenar por ${label}`}
        className={`inline-flex items-center gap-1 uppercase transition-colors hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 rounded ${
          isActive ? 'text-gray-900' : ''
        } ${align === 'right' ? 'flex-row-reverse' : ''}`}
      >
        {label}
        <Icon className={`h-3 w-3 ${isActive ? 'text-indigo-600' : 'text-gray-300'}`} />
      </button>
    </th>
  )
}
