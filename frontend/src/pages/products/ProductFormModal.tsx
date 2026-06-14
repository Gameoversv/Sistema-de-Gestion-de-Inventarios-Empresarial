import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { X } from 'lucide-react'
import toast from 'react-hot-toast'
import type { ProductResponse } from '@/types/index'
import { useCreateProduct, useUpdateProduct, useCategories } from '@/hooks/useProducts'

const schema = z.object({
  sku: z.string().min(1, 'SKU requerido').max(50),
  name: z.string().min(1, 'Nombre requerido').max(200),
  description: z.string().optional(),
  price: z.string().min(1),
  stock: z.string().min(1),
  minimumStock: z.string().min(1),
  categoryId: z.string().min(1, 'Categoría requerida'),
  active: z.boolean().optional(),
})

type FormData = z.infer<typeof schema>

interface Props {
  product?: ProductResponse | null
  onClose: () => void
}

export function ProductFormModal({ product, onClose }: Props) {
  const isEdit = !!product
  const { data: categories = [] } = useCategories()
  const createMutation = useCreateProduct()
  const updateMutation = useUpdateProduct()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  useEffect(() => {
    if (product) {
      reset({
        sku: product.sku,
        name: product.name,
        description: product.description ?? '',
        price: String(product.price),
        stock: String(product.stock),
        minimumStock: String(product.minimumStock),
        categoryId: String(product.categoryId),
        active: product.active,
      })
    } else {
      reset({ active: true, stock: '0', minimumStock: '0', price: '0' })
    }
  }, [product, reset])

  const onSubmit = async (data: FormData) => {
    try {
      if (isEdit && product) {
        await updateMutation.mutateAsync({
          id: product.id,
          body: {
            sku: data.sku,
            name: data.name,
            description: data.description,
            price: Number(data.price),
            minimumStock: Number(data.minimumStock),
            categoryId: Number(data.categoryId),
            active: data.active ?? true,
          },
        })
        toast.success('Producto actualizado')
      } else {
        await createMutation.mutateAsync({
          sku: data.sku,
          name: data.name,
          description: data.description,
          price: Number(data.price),
          stock: Number(data.stock),
          minimumStock: Number(data.minimumStock),
          categoryId: Number(data.categoryId),
        })
        toast.success('Producto creado')
      }
      onClose()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al guardar'
      toast.error(msg)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-900">
            {isEdit ? 'Editar producto' : 'Nuevo producto'}
          </h2>
          <button
            onClick={onClose}
            className="rounded-md p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 px-6 py-5">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-gray-700">SKU *</label>
              <input
                {...register('sku')}
                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                placeholder="PROD-001"
              />
              {errors.sku && <p className="mt-1 text-xs text-red-600">{errors.sku.message}</p>}
            </div>
            <div>
              <label className="text-xs font-medium text-gray-700">Categoría *</label>
              <select
                {...register('categoryId')}
                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              >
                <option value="">Seleccionar...</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              {errors.categoryId && (
                <p className="mt-1 text-xs text-red-600">{errors.categoryId.message}</p>
              )}
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Nombre *</label>
            <input
              {...register('name')}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              placeholder="Nombre del producto"
            />
            {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>}
          </div>

          <div>
            <label className="text-xs font-medium text-gray-700">Descripción</label>
            <textarea
              {...register('description')}
              rows={2}
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
              placeholder="Descripción opcional"
            />
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="text-xs font-medium text-gray-700">Precio *</label>
              <input
                {...register('price')}
                type="number"
                step="0.01"
                min="0"
                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              {errors.price && <p className="mt-1 text-xs text-red-600">{errors.price.message}</p>}
            </div>
            <div>
              <label className="text-xs font-medium text-gray-700">Stock inicial</label>
              <input
                {...register('stock')}
                type="number"
                min="0"
                disabled={isEdit}
                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:bg-gray-50 disabled:text-gray-400"
              />
            </div>
            <div>
              <label className="text-xs font-medium text-gray-700">Stock mínimo</label>
              <input
                {...register('minimumStock')}
                type="number"
                min="0"
                className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>
          </div>

          {isEdit && (
            <div className="flex items-center gap-2">
              <input
                {...register('active')}
                type="checkbox"
                id="active"
                className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              <label htmlFor="active" className="text-sm text-gray-700">Producto activo</label>
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2 border-t border-gray-100">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              {isSubmitting ? 'Guardando...' : isEdit ? 'Guardar cambios' : 'Crear producto'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
