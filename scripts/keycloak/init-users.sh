#!/bin/sh
# Keycloak post-import initialization:
# - Creates custom client scopes
# - Assigns scopes to clients as optional
# - Creates test users with roles
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

create_scope() {
  local name="$1" desc="$2"
  kc_post "/client-scopes" \
    "{\"name\":\"$name\",\"description\":\"$desc\",\"protocol\":\"openid-connect\",\"type\":\"none\",\"attributes\":{\"include.in.token.scope\":\"true\",\"display.on.consent.screen\":\"true\",\"consent.screen.text\":\"$desc\"}}" \
    2>/dev/null || echo "  scope '$name' already exists — skipping"
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

# ── Test users ──────────────────────────────────────────────────────────────
echo "==> Creating test users..."

create_user() {
  local username="$1" password="$2" email="$3" first="$4" role="$5"

  # Create (ignore conflict if already exists)
  kc_post "/users" \
    "{\"username\":\"$username\",\"email\":\"$email\",\"firstName\":\"$first\",\"lastName\":\"Test\",\"enabled\":true,\"emailVerified\":true}" \
    2>/dev/null || true

  USER_ID=$(kc_get "/users?username=$username&exact=true" | jq -r '.[0].id // empty')
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
