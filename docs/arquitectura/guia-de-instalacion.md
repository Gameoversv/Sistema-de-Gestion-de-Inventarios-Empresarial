# Guía de Instalación

Levantar el sistema desde cero, verificar que funciona y qué hacer cuando no. Cubre la parte de *"guías de instalación"* del entregable de Documentación Técnica.

Para operar y mantener un sistema ya instalado, ver el [manual de mantenimiento](../operacion/manual-mantenimiento.md).

---

## Requisitos previos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker + Docker Compose | Compose v2 (`docker compose`, sin guion) | Levantar el stack completo |
| Git | cualquiera reciente | Clonar |
| **(solo desarrollo)** JDK | 21 | Compilar el backend fuera de Docker |
| **(solo desarrollo)** Node | 20 | Servir el frontend en caliente |

Para **ejecutar** el sistema no hace falta ni JDK ni Node: las imágenes se construyen dentro de Docker. Solo se necesitan para desarrollar con recarga.

**Puertos que deben estar libres en el host:** 3000, 3001, 3200, 5432, 8080, 8180, 9090, 9093, 12345. Todos son configurables por `.env` si alguno choca.

---

## Instalación estándar

```bash
git clone https://github.com/Gameoversv/Sistema-de-Gestion-de-Inventarios-Empresarial.git
cd Sistema-de-Gestion-de-Inventarios-Empresarial

# Variables de entorno: copiar la plantilla y rellenar los secretos
cp .env.example .env
#  ← editar .env: cambiar TODOS los valores changeme_* antes de seguir

# Levantar los 14 servicios
docker compose up -d

# Seguir el arranque
docker compose logs -f backend keycloak
```

El arranque completo tarda **un par de minutos**: Keycloak importa el realm y el backend espera a que esté sano antes de aceptar peticiones. No es un cuelgue; es el `depends_on: service_healthy` haciendo su trabajo.

### Elegir el perfil antes de levantar

`SPRING_PROFILES_ACTIVE` en `.env` cambia más de lo que su nombre sugiere. Decidir **antes**, porque cambiarlo obliga a reiniciar el backend:

| Objetivo | Perfil | Por qué |
|---|---|---|
| Desarrollo local | `dev` (por defecto) | CORS abierto, log de texto legible |
| **Presentación / trabajar los dashboards de logs** | **`demo`** | Log JSON con los 6 campos MDC **y** CORS a `localhost:3000` |
| Espejo de producción, pruebas contra el desplegado | `staging` | Log JSON, pero CORS a dominios reales: **el frontend local queda bloqueado** |

> Para la demo, el perfil es **`demo`**, no `staging`. El panel de logs necesita JSON estructurado —sin él no filtra por usuario ni endpoint— y a la vez CORS hacia el frontend local; `demo` es el único que da las dos cosas. Usar `staging` para la demo deja la interfaz sin poder hablar con la API. (El README menciona `staging` para este caso; quedó desactualizado tras crear `demo` en P-2a — manda esta guía.)

---

## Verificación post-instalación

Instalado no es lo mismo que funcionando. Comprobar en este orden:

### 1. Todos los contenedores arriba y sanos

```bash
docker compose ps
```

Los 14 servicios en `running`. Los que tienen healthcheck deben marcar `healthy`, no solo `running`. `keycloak-init` aparece como `exited (0)`: es correcto, es un one-shot que crea scopes y usuarios y termina.

### 2. Los puntos de entrada responden

| Comprobación | Comando | Esperado |
|---|---|---|
| Backend vivo | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| Keycloak vivo | `curl -s http://localhost:8180/health/ready` | contiene `UP` |
| Swagger UI | abrir `http://localhost:8080/swagger-ui.html` | carga la especificación |
| Frontend | abrir `http://localhost:3000` | pantalla de login |
| Grafana | abrir `http://localhost:3001` | 4 dashboards provisionados |

### 3. La cadena de autenticación cierra

La verificación que de verdad importa: sin token se rechaza, con token válido se responde.

```bash
# Sin token → 401 (no hay identidad)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/products
# → 401

# Obtener un token del usuario admin de prueba (ajustar la contraseña del .env)
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=inventory-frontend \
  -d username=inv_admin \
  -d password="$KC_USER_ADMIN_PASSWORD" \
  -d scope="openid product:view" | jq -r .access_token)

# Con token → 200
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:8080/products
# → 200
```

Si el segundo `curl` da **403** en vez de 200, casi siempre es que falta el `scope` en la petición del token: sin `product:view` en el `scope`, el backend no concede la autoridad aunque el usuario tenga el rol. Es el comportamiento correcto (RNF-02), no un fallo.

Los cuatro usuarios de prueba: `inv_admin`, `inv_clerk`, `inv_auditor`, `inv_viewer`, con las contraseñas del `.env`. Sus permisos, en [RF-22](../requisitos/requisitos-funcionales.md#rf-22--matriz-de-permisos-aplicada-por-endpoint).

---

## Arranque limpio y reinicio

```bash
# Reiniciar conservando los datos
docker compose restart

# Parar sin borrar
docker compose down

# Borrar TODO, incluidos los datos y el realm de Keycloak
docker compose down -v
```

`down -v` borra los nueve volúmenes con nombre. Es lo que hace falta para un arranque de cero **y** lo que nunca hay que hacer sobre datos que interesen. El ensayo de la presentación es exactamente un `down -v && up`, y ahí es donde muerde P-2b (ver abajo).

---

## Cuando no arranca

| Síntoma | Causa probable | Salida |
|---|---|---|
| `keycloak-init` falla con `duplicate key … uk_cli_scope` | **P-2b**: el script no es idempotente y el realm ya existía | En un arranque limpio (`down -v`) no ocurre. Sobre un realm existente, es inofensivo salvo que ensucia el panel de eventos. Corrección pendiente, issue #45 |
| El backend reinicia en bucle esperando a Keycloak | Keycloak aún no está `healthy`; el backend depende de él | Esperar. Si persiste más de 2-3 min, revisar `docker compose logs keycloak` |
| El frontend carga pero toda petición da error de CORS | Perfil `staging`/`prod` con el frontend en `localhost` | Cambiar a perfil `demo` y `docker compose up -d backend` |
| El panel de logs de Grafana no filtra por usuario ni endpoint | Perfil `dev`: log de texto plano | Cambiar a `demo` o `staging` |
| Un puerto no se puede enlazar (`address already in use`) | Choque de puertos en el host | Cambiar el puerto correspondiente en `.env` |
| Los tests de integración fallan con `Could not find a valid Docker environment` | **C-4**: Testcontainers no arranca sobre Docker Desktop en Windows | Los IT pasan en runners Linux (GitHub Actions). En local, en Windows, no. Issue #49 |

El paso `docker compose logs <servicio>` resuelve la mayoría: cada healthcheck problemático deja rastro. Las sondas no evidentes de Keycloak y node-exporter están explicadas en [vista-de-componentes.md](vista-de-componentes.md#las-sondas-que-costaron-encontrar).

---

## Desarrollo con recarga

Para iterar sin reconstruir imágenes en cada cambio, levantar solo la infraestructura en Docker y las dos apps en local:

```bash
# Infraestructura sí, apps no
docker compose up -d postgres keycloak keycloak-db keycloak-init \
  prometheus grafana tempo loki alloy alertmanager node-exporter postgres-exporter

# Backend en caliente
cd backend && ./mvnw spring-boot:run

# Frontend en caliente
cd frontend && npm install && npm run dev
```

Con esto el backend corre en perfil `dev` contra la infraestructura containerizada, y el frontend recarga en el navegador a cada guardado.
