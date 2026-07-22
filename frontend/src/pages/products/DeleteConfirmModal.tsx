/** Modal dialog that asks the user to confirm before permanently deleting a product. */
import { X, AlertTriangle } from 'lucide-react'
import toast from 'react-hot-toast'
import type { ProductResponse } from '@/types/index'
import { useDeleteProduct } from '@/hooks/useProducts'

interface Props {
  product: ProductResponse
  onClose: () => void
}

export function DeleteConfirmModal({ product, onClose }: Props) {
  const deleteMutation = useDeleteProduct()

  const handleConfirm = async () => {
    try {
      await deleteMutation.mutateAsync(product.id)
      toast.success(`Producto "${product.name}" desactivado`)
      onClose()
    } catch {
      toast.error('Error al desactivar producto')
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <div className="flex items-center gap-2 text-amber-600">
            <AlertTriangle className="h-5 w-5" />
            <h2 className="text-base font-semibold">Confirmar desactivación</h2>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1.5 text-gray-400 hover:bg-gray-100"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-gray-600">
            ¿Desactivar el producto <strong className="text-gray-900">{product.name}</strong>?
            El producto no se eliminará, quedará marcado como inactivo.
          </p>
          <div className="flex justify-end gap-3">
            <button
              onClick={onClose}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Cancelar
            </button>
            <button
              onClick={handleConfirm}
              disabled={deleteMutation.isPending}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
            >
              {deleteMutation.isPending ? 'Desactivando...' : 'Desactivar'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
