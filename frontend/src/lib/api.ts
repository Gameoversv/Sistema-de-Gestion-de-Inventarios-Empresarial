import axios from 'axios'
import keycloak from './keycloak'

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
        await keycloak.updateToken(30)
        const originalRequest = error.config
        originalRequest.headers.Authorization = `Bearer ${keycloak.token}`
        return api(originalRequest)
      } catch {
        keycloak.login()
      }
    }
    return Promise.reject(error)
  },
)

export default api
