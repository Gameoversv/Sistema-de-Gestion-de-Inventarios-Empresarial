/** React Query hooks para la gestion de usuarios del realm (permiso user:manage). */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface UserResponse {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  enabled: boolean
  roles: string[]
}

export interface UserCreateRequest {
  username: string
  email: string
  firstName?: string
  lastName?: string
  password: string
  roles: string[]
}

export function useUsers(search: string) {
  return useQuery({
    queryKey: ['users', search],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (search) params.set('search', search)
      const { data } = await api.get<UserResponse[]>(`/api/users?${params}`)
      return data
    },
  })
}

export function useAssignableRoles() {
  return useQuery({
    queryKey: ['users', 'roles'],
    queryFn: async () => (await api.get<string[]>('/api/users/roles')).data,
    staleTime: 5 * 60 * 1000,
  })
}

/** Invalida el listado tras cualquier escritura: los roles se leen de Keycloak, no de cache local. */
function useUserMutation<T>(fn: (input: T) => Promise<unknown>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export function useCreateUser() {
  return useUserMutation((body: UserCreateRequest) => api.post('/api/users', body))
}

export function useReplaceRoles() {
  return useUserMutation(({ id, roles }: { id: string; roles: string[] }) =>
    api.put(`/api/users/${id}/roles`, { roles }),
  )
}

export function useToggleUser() {
  return useUserMutation((user: UserResponse) =>
    api.put(`/api/users/${user.id}`, {
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      enabled: !user.enabled,
    }),
  )
}

export function useDeleteUser() {
  return useUserMutation((id: string) => api.delete(`/api/users/${id}`))
}
