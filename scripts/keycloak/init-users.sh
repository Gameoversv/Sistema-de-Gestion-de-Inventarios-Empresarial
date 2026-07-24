#!/bin/sh
# Keycloak post-import initialization:
# - Creates custom client scopes
# - Assigns scopes to clients as optional
# - Creates test users with roles
#
# Idempotente (P-2b): comprueba la existencia de cada scope y usuario ANTES de
# crearlo. Sin esto, un segundo arranque sobre un realm ya inicializado lanzaba
# POST sobre recursos existentes; Keycloak respondia 409 y registraba la
# violacion de la restriccion uk_cli_scope en su panel de eventos. Reejecutar
# ahora no crea ruido: lo que ya existe se salta.
set -e

apk add --no-cache curl jq > /dev/null 2>&1

KC=${KC_URL:-http://keycloak:8080}
REALM=${KC_REALM:-inventory}
ADMIN=${KEYCLOAK_ADMIN:-admin}
ADMIN_PASS=${KEYCLOAK_ADMIN_PASSWORD}

echo "==> Authenticating against Keycloak master realm..."
TOKEN=$(curl -sf "$KC/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: failed to obtain admin token"
  exit 1
fi

kc_get()  { curl -sf    "$KC/admin/realms/$REALM$1" -H "Authorization: Bearer $TOKEN"; }
kc_post() { curl -sf -X POST "$KC/admin/realms/$REALM$1" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$2"; }
kc_put()  { curl -sf -X PUT  "$KC/admin/realms/$REALM$1" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$2" || true; }

# ── Custom client scopes ────────────────────────────────────────────────────
echo "==> Creating custom client scopes..."

# Nombres de los scopes que ya existen, leidos una sola vez. Se consulta contra
# esta lista antes de crear, para no lanzar un POST que chocaria con uk_cli_scope.
EXISTING_SCOPES=$(kc_get "/client-scopes" | jq -r '.[].name')

create_scope() {
  local name="$1" desc="$2"
  if echo "$EXISTING_SCOPES" | grep -Fxq "$name"; then
    echo "  scope '$name' already exists — skipping"
    return
  fi
  kc_post "/client-scopes" \
    "{\"name\":\"$name\",\"description\":\"$desc\",\"protocol\":\"openid-connect\",\"type\":\"none\",\"attributes\":{\"include.in.token.scope\":\"true\",\"display.on.consent.screen\":\"true\",\"consent.screen.text\":\"$desc\"}}" \
    > /dev/null && echo "  ✓ scope '$name' created"
}

create_scope "product:view"   "View product catalog"
create_scope "product:manage" "Create and update products"
create_scope "stock:view"     "View stock levels"
create_scope "stock:manage"   "Manage stock movements"
create_scope "report:view"    "View reports and analytics"
create_scope "user:manage"    "Manage system users"
create_scope "audit:view"     "View audit logs"

# ── Add custom scopes as optional to inventory-frontend ────────────────────
echo "==> Adding custom scopes to clients..."

FRONTEND_ID=$(kc_get "/clients?clientId=inventory-frontend" | jq -r '.[0].id // empty')
BACKEND_ID=$(kc_get  "/clients?clientId=inventory-backend"  | jq -r '.[0].id // empty')

for scope_name in "product:view" "product:manage" "stock:view" "stock:manage" "report:view" "user:manage" "audit:view"; do
  SCOPE_ID=$(kc_get "/client-scopes" | jq -r --arg n "$scope_name" '.[] | select(.name==$n) | .id // empty')
  if [ -n "$SCOPE_ID" ]; then
    if [ -n "$FRONTEND_ID" ]; then
      kc_put "/clients/$FRONTEND_ID/optional-client-scopes/$SCOPE_ID" ""
    fi
    if [ -n "$BACKEND_ID" ]; then
      kc_put "/clients/$BACKEND_ID/optional-client-scopes/$SCOPE_ID" ""
    fi
    echo "  ✓ scope '$scope_name' linked to clients"
  else
    echo "  WARNING: scope '$scope_name' not found — skipping link"
  fi
done

# ── Scope mappings por rol (G-8) ─────────────────────────────────────────────
# Ata cada client scope a los roles de realm autorizados a solicitarlo. Es la
# correccion de raiz que propone el informe G-6: sin esto Keycloak concede
# cualquier optional scope a cualquier usuario autenticado (issue #43). El mapeo
# espeja SCOPES_BY_ROLE del backend (ADR-002).
#
# ATENCION: que esto llegue a gatear la CADENA scope emitida en el token es lo
# que verifica el paso "G-8 — scope emission gated by role" de staging.yml. Si
# ese test sale en rojo, Keycloak no gatea el string via scope-mappings y G-8
# necesita otro enfoque. Pase lo que pase, NO se retira el mapa Java: es el
# control efectivo hasta que este test este en verde.
echo "==> Assigning realm-role scope mappings to client scopes (G-8)..."

assign_scope_roles() {
  scope_name="$1"
  shift
  SCOPE_ID=$(kc_get "/client-scopes" | jq -r --arg n "$scope_name" '.[] | select(.name==$n) | .id // empty')
  if [ -z "$SCOPE_ID" ]; then
    echo "  WARNING: scope '$scope_name' not found — skipping scope-mapping"
    return
  fi
  reps="[]"
  for role in "$@"; do
    rep=$(kc_get "/roles/$role" 2>/dev/null)
    if [ -n "$rep" ] && [ "$rep" != "null" ]; then
      reps=$(echo "$reps" | jq --argjson r "$rep" '. + [$r]')
    else
      echo "  WARNING: role '$role' not found — skipping for '$scope_name'"
    fi
  done
  # POST es idempotente: reasignar un rol ya presente es un no-op (204).
  kc_post "/client-scopes/$SCOPE_ID/scope-mappings/realm" "$reps" > /dev/null 2>&1 \
    && echo "  ✓ '$scope_name' → $*" \
    || echo "  '$scope_name' scope-mapping ya establecido — saltando"
}

assign_scope_roles "product:view"   inventory-admin warehouse-clerk auditor viewer
assign_scope_roles "product:manage" inventory-admin warehouse-clerk
assign_scope_roles "stock:view"     inventory-admin warehouse-clerk auditor viewer
assign_scope_roles "stock:manage"   inventory-admin warehouse-clerk
assign_scope_roles "report:view"    inventory-admin warehouse-clerk auditor viewer
assign_scope_roles "user:manage"    inventory-admin
assign_scope_roles "audit:view"     inventory-admin auditor

# ── Test users ──────────────────────────────────────────────────────────────
echo "==> Creating test users..."

create_user() {
  local username="$1" password="$2" email="$3" first="$4" role="$5"

  # Crear solo si no existe: un POST sobre un usuario ya presente devuelve 409 y
  # ensucia el log. La contrasena y el rol se re-aplican igual mas abajo, asi
  # que un usuario preexistente queda con el estado esperado sin recrearlo.
  USER_ID=$(kc_get "/users?username=$username&exact=true" | jq -r '.[0].id // empty')
  if [ -z "$USER_ID" ]; then
    kc_post "/users" \
      "{\"username\":\"$username\",\"email\":\"$email\",\"firstName\":\"$first\",\"lastName\":\"Test\",\"enabled\":true,\"emailVerified\":true}" \
      > /dev/null || true
    USER_ID=$(kc_get "/users?username=$username&exact=true" | jq -r '.[0].id // empty')
  fi
  if [ -z "$USER_ID" ]; then
    echo "  ERROR: could not resolve user '$username'"
    return 1
  fi

  # Set password (non-temporary)
  curl -sf -X PUT "$KC/admin/realms/$REALM/users/$USER_ID/reset-password" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}" || true

  # Assign realm role
  ROLE_REP=$(kc_get "/roles/$role" 2>/dev/null)
  if [ -n "$ROLE_REP" ] && [ "$ROLE_REP" != "null" ]; then
    curl -sf -X POST "$KC/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "[$ROLE_REP]" || true
    echo "  ✓ $username ($role)"
  else
    echo "  WARNING: role '$role' not found for user '$username'"
  fi
}

create_user \
  "${KC_USER_ADMIN_USERNAME:-inv_admin}" \
  "${KC_USER_ADMIN_PASSWORD}" \
  "admin@inventory.local" \
  "Admin" \
  "inventory-admin"

create_user \
  "${KC_USER_CLERK_USERNAME:-inv_clerk}" \
  "${KC_USER_CLERK_PASSWORD}" \
  "clerk@inventory.local" \
  "Clerk" \
  "warehouse-clerk"

create_user \
  "${KC_USER_AUDITOR_USERNAME:-inv_auditor}" \
  "${KC_USER_AUDITOR_PASSWORD}" \
  "auditor@inventory.local" \
  "Auditor" \
  "auditor"

create_user \
  "${KC_USER_VIEWER_USERNAME:-inv_viewer}" \
  "${KC_USER_VIEWER_PASSWORD}" \
  "viewer@inventory.local" \
  "Viewer" \
  "viewer"

echo ""
echo "✅ Keycloak initialization complete."
echo "   Realm  : $KC/realms/$REALM"
echo "   Console: $KC/admin/$REALM/console"
