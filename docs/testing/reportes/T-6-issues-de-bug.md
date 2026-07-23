# T-6 — Los defectos encontrados pasan a ser issues de GitHub

**Fecha:** 2026-07-23
**Alcance:** 15 issues de bug (#43–#57) y 3 charters de testing exploratorio (#58–#60)
**Estado:** cerrado

---

## Por qué

El repositorio tenía 13 issues y **las 13 eran épicas de fase**. Ningún bug, pese a que el proyecto llevaba varios encontrados, documentados y corregidos. El enunciado evalúa el uso de issues, y la capa 8 de testing —exploratorio— pide explícitamente *"charters, bugs encontrados, escenarios"*.

El material ya estaba escrito: los informes de QA en esta misma carpeta y los hallazgos de la Ola 4. Faltaba darles trazabilidad propia, fuera del cuerpo de un PR donde nadie los va a buscar.

## Qué se creó

| Bloque | Cantidad | Estado |
|---|---|---|
| Bugs abiertos | 7 | #43…#49 |
| Bugs ya corregidos | 8 | #50…#57, cerradas apuntando a su PR |
| Charters ejecutados | 2 | #58, #59, cerrados |
| Charter pendiente | 1 | #60, bloquea P-3 |

El repositorio pasa de 13 issues a 31.

### Los 7 defectos abiertos

| # | Defecto | Severidad |
|---|---|---|
| #43 | Keycloak emite cualquier scope a cualquier usuario autenticado | ALTA |
| #44 | `AuthContext.tsx` conserva los dos defectos de scopes corregidos en el backend | MEDIA |
| #45 | `keycloak-init` no es idempotente: `duplicate key … uk_cli_scope` | MEDIA |
| #46 | El token de ZAP caduca a los 300 s y el resto del escaneo corre sin autenticar | ALTA |
| #47 | `staging.yml` inyecta `JWT_SECRET` y `JWT_EXPIRATION_MS` que no lee nadie | BAJA / MEDIA |
| #48 | `user:manage` no protege ningún endpoint | MEDIA |
| #49 | Testcontainers no arranca sobre Docker Desktop y bloquea 7 etapas de Jenkins | ALTA |

### Los 8 ya corregidos

Se registraron **cerrados**, cada uno enlazando el PR que lo arregló. El cuerpo de cada uno declara que la issue se abrió después de la corrección: son issues retroactivas y decirlo cuesta una línea. Registrarlas sin avisar habría sido simular un flujo de trabajo que no ocurrió.

G-4 y G-5 (PR #33) · check de integración vacío (PR #32) · badge de cobertura falso, cobertura de frontend falsa y spotless desactivado (PR #37) · README con `/api/v1` inexistente (PR #40) · CORS de staging (PR #42).

## Dos hallazgos nuevos

Redactar las issues obligó a releer el código, no solo los informes. Salieron dos cosas que el plan no tenía anotadas.

**1. El frontend arrastra los dos defectos, no uno.** El plan registraba la tarea G-3a como "unión de scopes en `AuthContext.tsx`", es decir, solo G-4. Al abrir el archivo:

```ts
function permittedScopesForRoles(roles: string[]): Set<string> {
  if (roles.includes('inventory-admin')) { return new Set([...]) }
  if (roles.includes('warehouse-clerk')) { return new Set([...]) }
  if (roles.includes('auditor'))         { return new Set([...]) }
  // viewer and any other role: read-only
  return new Set(['product:view', 'stock:view', 'report:view'])
}
```

Es copia literal de la versión de `SecurityConfig.java` anterior al PR #33, con **G-4 y G-5**. El fallback es el más visible de los dos: el frontend pinta pantallas que la API responde con 403, porque el backend ya deniega por defecto y la interfaz no.

**2. `JWT_SECRET` no está donde decía el plan.** La tarea S-4b lo situaba en `application-staging.yml`. No está ahí: está en `.github/workflows/staging.yml:38-39`, y con él un secreto de repositorio (`STAGING_JWT_SECRET`) vivo. Ningún Java lo lee — la autenticación valida contra el JWK set de Keycloak, no hay firma HMAC propia.

## Sobre los charters

Los dos ejecutados documentan sesiones reales con su timebox, sus escenarios y los bugs que salieron de cada una.

El de mayor valor metodológico es **#59**, sobre configuración que aparenta ejecutarse. Su heurística 3 —*¿cuántos elementos entran en el denominador de la métrica?*— es la que ningún test detecta: una cobertura del 100 % sobre un denominador recortado no informa de menos, informa del máximo posible, y por eso nadie la cuestiona. El valor real del frontend era 5,4 %.

El pendiente, **#60**, cubre el arranque desde cero y bloquea P-3. Se deja abierto a propósito: es trabajo real por hacer, no documentación de algo ya ocurrido.

## Lo que esto no arregla

Ninguno de los 7 defectos abiertos queda corregido por esta tarea. Lo que cambia es que dejan de vivir dentro del cuerpo de un PR cerrado y pasan a ser trabajo rastreable, priorizable y visible desde la pestaña de issues.
