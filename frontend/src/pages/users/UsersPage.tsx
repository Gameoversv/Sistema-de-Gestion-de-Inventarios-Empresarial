/** Gestion de usuarios, roles y permisos del realm. Toda la pagina exige el permiso user:manage. */
import { useState } from 'react'
import { UserPlus, Trash2, X } from 'lucide-react'
import {
  useUsers,
  useAssignableRoles,
  useCreateUser,
  useReplaceRoles,
  useToggleUser,
  useDeleteUser,
  type UserResponse,
} from '@/hooks/useUsers'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { Badge } from '@/components/ui/Badge'

export function UsersPage() {
  const [search, setSearch] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  const { data: users, isLoading, isError } = useUsers(search)
  const { data: roles = [] } = useAssignableRoles()
  const replaceRoles = useReplaceRoles()
  const toggleUser = useToggleUser()
  const deleteUser = useDeleteUser()

  function toggleRole(user: UserResponse, role: string) {
    const next = user.roles.includes(role)
      ? user.roles.filter((r) => r !== role)
      : [...user.roles, role]
    replaceRoles.mutate({ id: user.id, roles: next })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">Usuarios</h2>
          <p className="text-sm text-gray-500">
            Las identidades viven en Keycloak. Los cambios de rol se aplican al siguiente token.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          <UserPlus className="h-4 w-4" />
          Nuevo usuario
        </button>
      </div>

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Buscar por nombre, usuario o correo"
        aria-label="Buscar usuarios"
        className="w-full max-w-sm rounded-lg border border-gray-300 px-3 py-2 text-sm"
      />

      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonTable rows={5} />
          </div>
        ) : isError ? (
          <p className="p-6 text-sm text-red-600">
            No se pudo cargar la lista. Revisa que la gestión de usuarios esté configurada.
          </p>
        ) : !users?.length ? (
          <p className="p-6 text-sm text-gray-500">Ningún usuario coincide con la búsqueda.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">Usuario</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">Correo</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-500">Roles</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase text-gray-500">Estado</th>
                <th className="px-4 py-3 text-center text-xs font-medium uppercase text-gray-500">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {users.map((user) => (
                <tr key={user.id}>
                  <td className="px-4 py-3 font-medium text-gray-900">{user.username}</td>
                  <td className="px-4 py-3 text-gray-600">{user.email}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {roles.map((role) => {
                        const active = user.roles.includes(role)
                        return (
                          <button
                            key={role}
                            onClick={() => toggleRole(user, role)}
                            disabled={replaceRoles.isPending}
                            aria-pressed={active}
                            className={`rounded-full px-2.5 py-1 text-xs transition-colors ${
                              active
                                ? 'bg-indigo-600 text-white'
                                : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                            }`}
                          >
                            {role}
                          </button>
                        )
                      })}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => toggleUser.mutate(user)}
                      aria-label={user.enabled ? 'Desactivar usuario' : 'Activar usuario'}
                    >
                      <Badge variant={user.enabled ? 'green' : 'gray'}>
                        {user.enabled ? 'Activo' : 'Inactivo'}
                      </Badge>
                    </button>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => {
                        if (confirm(`¿Eliminar a ${user.username} del realm?`)) {
                          deleteUser.mutate(user.id)
                        }
                      }}
                      aria-label={`Eliminar ${user.username}`}
                      className="text-gray-400 hover:text-red-600"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showCreate && <CreateUserModal roles={roles} onClose={() => setShowCreate(false)} />}
    </div>
  )
}

function CreateUserModal({ roles, onClose }: { roles: string[]; onClose: () => void }) {
  const create = useCreateUser()
  const [form, setForm] = useState({
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    roles: [] as string[],
  })

  const invalid = !form.username || !form.email || form.password.length < 8

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-900">Nuevo usuario</h3>
          <button onClick={onClose} aria-label="Cerrar" className="text-gray-400 hover:text-gray-600">
            <X className="h-5 w-5" />
          </button>
        </div>

        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate(form, { onSuccess: onClose })
          }}
        >
          {(
            [
              ['username', 'Usuario *', 'text'],
              ['email', 'Correo *', 'email'],
              ['firstName', 'Nombre', 'text'],
              ['lastName', 'Apellido', 'text'],
              ['password', 'Contraseña * (mínimo 8)', 'password'],
            ] as const
          ).map(([field, label, type]) => (
            <div key={field}>
              <label htmlFor={field} className="mb-1 block text-xs font-medium text-gray-700">
                {label}
              </label>
              <input
                id={field}
                type={type}
                value={form[field]}
                onChange={(e) => setForm({ ...form, [field]: e.target.value })}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              />
            </div>
          ))}

          <fieldset>
            <legend className="mb-1 text-xs font-medium text-gray-700">Roles</legend>
            <div className="flex flex-wrap gap-2">
              {roles.map((role) => (
                <label key={role} className="flex items-center gap-1.5 text-xs text-gray-700">
                  <input
                    type="checkbox"
                    checked={form.roles.includes(role)}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        roles: e.target.checked
                          ? [...form.roles, role]
                          : form.roles.filter((r) => r !== role),
                      })
                    }
                  />
                  {role}
                </label>
              ))}
            </div>
          </fieldset>

          {create.isError && (
            <p className="text-xs text-red-600">
              No se pudo crear. ¿Ya existe ese usuario o correo?
            </p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={invalid || create.isPending}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              Crear usuario
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
