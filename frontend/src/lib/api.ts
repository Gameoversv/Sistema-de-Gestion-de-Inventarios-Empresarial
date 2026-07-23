/** Pre-configured Axios instance that automatically attaches the Keycloak bearer token to every outgoing request. */
import axios from 'axios'
import keycloak from './keycloak'
import { endSession, MIN_TOKEN_VALIDITY_SECONDS } from './session'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  const token = keycloak.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      try {
        await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS)
        const originalRequest = error.config
        originalRequest.headers.Authorization = `Bearer ${keycloak.token}`
        return api(originalRequest)
      } catch {
        // Misma salida que el manejador proactivo de session.ts: descartar el token muerto antes
        // de redirigir. Antes solo llamaba a login() y el token caducado seguia en memoria.
        endSession(keycloak)
      }
    }
    return Promise.reject(error)
  },
)

export default api
