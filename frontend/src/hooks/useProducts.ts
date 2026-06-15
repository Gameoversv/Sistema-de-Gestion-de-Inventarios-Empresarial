import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import type { ProductResponse, ProductCreateRequest, ProductUpdateRequest, Page } from '@/types/index'

interface ProductFilters {
  search?: string
  categoryId?: number
  active?: boolean
  page?: number
  size?: number
}

export function useProducts(filters: ProductFilters = {}) {
  return useQuery({
    queryKey: ['products', filters],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (filters.search) params.set('search', filters.search)
      if (filters.categoryId != null) params.set('categoryId', String(filters.categoryId))
      if (filters.active != null) params.set('active', String(filters.active))
      params.set('page', String(filters.page ?? 0))
      params.set('size', String(filters.size ?? 20))
      params.set('sort', 'name')
      const { data } = await api.get<Page<ProductResponse>>(`/products?${params}`)
      return data
    },
  })
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const { data } = await api.get('/categories')
      return data as { id: number; name: string; description: string | null }[]
    },
    staleTime: 60_000,
  })
}

export function useCreateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductCreateRequest) => api.post('/products', body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  })
}

export function useUpdateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ProductUpdateRequest }) =>
      api.put(`/products/${id}`, body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  })
}

export function useDeleteProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/products/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  })
}
