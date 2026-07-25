// T-3 — Performance testing con k6. Cubre load, stress, usuarios concurrentes,
// tiempo de respuesta y throughput contra el sistema desplegado, como exige el
// enunciado. Los umbrales hacen fallar el job si el rendimiento se degrada.
//
// Se ejecuta en CI (e2e.yml) contra el stack demo ya desplegado. Localmente:
//   k6 run -e BASE_URL=http://localhost:8080 -e KC_URL=http://localhost:8180 \
//          -e KC_PASSWORD=Admin123 scripts/k6/load-test.js

import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const KC = __ENV.KC_URL || 'http://localhost:8180'
const KC_USER = __ENV.KC_USER || 'inv_admin'
const KC_PASSWORD = __ENV.KC_PASSWORD || 'Admin123'

export const options = {
  // Perfil combinado: carga sostenida (load) + pico de concurrencia (stress).
  stages: [
    { duration: '15s', target: 10 }, // ramp-up
    { duration: '30s', target: 10 }, // carga sostenida
    { duration: '15s', target: 25 }, // pico de usuarios concurrentes (stress)
    { duration: '10s', target: 0 }, // ramp-down
  ],
  thresholds: {
    // El enunciado pide tiempo de respuesta: p(95) por debajo de 500 ms.
    http_req_duration: ['p(95)<500'],
    // Menos del 1 % de peticiones fallidas bajo carga.
    http_req_failed: ['rate<0.01'],
    // Comprobación de negocio: casi todas las respuestas deben ser 200.
    checks: ['rate>0.99'],
  },
}

// setup() corre una vez: obtiene un token que comparten todos los VUs.
export function setup() {
  const res = http.post(`${KC}/realms/inventory/protocol/openid-connect/token`, {
    client_id: 'inventory-frontend',
    grant_type: 'password',
    username: KC_USER,
    password: KC_PASSWORD,
    scope: 'openid product:view stock:view report:view audit:view',
  })
  check(res, { 'token obtenido': (r) => r.status === 200 })
  return { token: res.json('access_token') }
}

const READ_ENDPOINTS = [
  '/products?page=0&size=20&sort=name',
  '/products?search=e&page=0&size=10',
  '/categories',
  '/api/reports/stock-summary',
  '/api/reports/dashboard-metrics',
  '/api/reports/low-stock',
  '/api/stock/movements?page=0&size=20',
]

export default function (data) {
  const params = {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'read' },
  }
  for (const endpoint of READ_ENDPOINTS) {
    const res = http.get(`${BASE}${endpoint}`, params)
    check(res, { 'status 200': (r) => r.status === 200 })
  }
  sleep(1)
}
