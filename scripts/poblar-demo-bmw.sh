#!/usr/bin/env bash
#
# Puebla la base local con el catálogo de demostración: taller de piezas de
# performance para BMW.
#
# Por qué por API y no por Flyway: los datos entran por Hibernate, así que
# Envers registra cada alta y cada movimiento. La pantalla de auditoría queda
# con historial real en vez de vacía, y `categories_aud` se puebla —cosa que el
# seed de V5 no hace, porque inserta por SQL directo (ver issue #91).
#
# V5__seed_data.sql se deja intacta a propósito: DataIntegrityIT verifica que
# ELEC-001 existe. Este script purga esas filas solo en la base local.
#
# Uso:
#   scripts/poblar-demo-bmw.sh
#
# Requisitos: el stack levantado (`docker compose up -d`) y jq no es necesario.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

BACKEND="${BACKEND_URL:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK_URL:-http://localhost:8180}"
CONTENEDOR_DB="${DB_CONTAINER:-inventory-postgres}"

# shellcheck disable=SC1091
set -a && source .env && set +a

SCOPES="openid product:view product:manage stock:view stock:manage report:view audit:view user:manage"

# ── utilidades ───────────────────────────────────────────────────────────────

json() { python -c "import sys,json;d=json.load(sys.stdin);print(eval('d$1'))"; }

esperar_backend() {
  echo "==> Esperando al backend en $BACKEND"
  for _ in $(seq 1 60); do
    if curl -sf --max-time 5 "$BACKEND/actuator/health" >/dev/null 2>&1; then
      echo "    listo"
      return 0
    fi
    sleep 5
  done
  echo "ERROR: el backend no respondió a tiempo" >&2
  exit 1
}

obtener_token() {
  local respuesta
  respuesta=$(curl -s --max-time 20 -X POST \
    "$KEYCLOAK/realms/${KC_REALM:-inventory}/protocol/openid-connect/token" \
    -d "grant_type=password" -d "client_id=inventory-frontend" \
    -d "username=$KC_USER_ADMIN_USERNAME" -d "password=$KC_USER_ADMIN_PASSWORD" \
    --data-urlencode "scope=$SCOPES")
  TOKEN=$(printf '%s' "$respuesta" | python -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))")
  if [ -z "$TOKEN" ]; then
    echo "ERROR: no se pudo obtener el token de Keycloak" >&2
    printf '%s\n' "$respuesta" | head -c 400 >&2
    exit 1
  fi
}

api() { # metodo ruta [cuerpo]
  local metodo="$1" ruta="$2" cuerpo="${3:-}"
  if [ -n "$cuerpo" ]; then
    curl -s --max-time 20 -X "$metodo" "$BACKEND$ruta" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$cuerpo"
  else
    curl -s --max-time 20 -X "$metodo" "$BACKEND$ruta" -H "Authorization: Bearer $TOKEN"
  fi
}

sql() { docker exec -i "$CONTENEDOR_DB" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"; }

# ── 1. purga del catálogo genérico ───────────────────────────────────────────

purgar() {
  echo "==> Purgando el catálogo genérico de V5 en la base local"
  sql "TRUNCATE stock_movements, stock_movements_aud, products_aud, products,
              categories_aud, categories RESTART IDENTITY CASCADE;" >/dev/null
  echo "    productos y categorías a cero"
}

# ── 2. catálogo ──────────────────────────────────────────────────────────────

# nombre|descripción
CATEGORIAS=(
  "Admisión y Filtros|Filtros de alto flujo, airboxes de carbono y conductos de admisión"
  "Escape y Downpipes|Downpipes deportivos, line-pipes y escapes traseros"
  "Turbo y Sobrealimentación|Turbos híbridos, wastegates y tuberías de carga"
  "Intercooler y Refrigeración|Intercoolers, radiadores y refrigeración de aceite"
  "Suspensión y Chasis|Coilovers, barras estabilizadoras y refuerzos de chasis"
  "Frenos|Kits de pinzas, discos perforados y pastillas de competición"
  "Software y Electrónica|Reprogramación de centralita, piggybacks y telemetría"
  "Transmisión y Embrague|Embragues reforzados, volantes bimasa y soportes"
)

# sku|nombre|descripción|precio|stock|mínimo|índice de categoría (1-based)
PRODUCTOS=(
  # ── Admisión ──
  "INT-EVN-001|Eventuri Carbon Intake B58|Admisión de fibra de carbono para B58 (M140i, M240i, 340i). Airbox sellado|1149.00|12|4|1"
  "INT-BMC-002|BMC Filtro Alto Flujo F80 M3|Filtro lavable de algodón para S55, recambio directo|189.50|40|10|1"
  "INT-VRS-003|VRSF Intake Kit N54 335i|Kit de admisión doble cono con conductos de aluminio|329.00|18|6|1"
  "INT-AFE-004|aFe Momentum GT S58 M3 G80|Admisión de dos entradas con filtro Pro 5R|899.00|7|5|1"
  "INT-KNF-005|K&N Filtro Reemplazo N55|Filtro de recambio lavable para N55 335i/435i|94.99|55|15|1"

  # ── Escape ──
  "EXH-AKR-001|Akrapovic Evolution Line M4 F82|Escape completo de titanio con colas de carbono|6890.00|3|2|2"
  "EXH-VRS-002|VRSF Downpipes Catless N54|Par de downpipes de 3\" sin catalizador, acero inoxidable|549.00|14|5|2"
  "EXH-SUP-003|Supersprint Cat-Back E92 335i|Escape trasero con válvulas y salidas de 90 mm|1780.00|5|3|2"
  "EXH-MPE-004|Downpipe Deportivo B58 G20|Downpipe con catalizador deportivo de 200 celdas|629.00|9|4|2"
  "EXH-REM-005|Remus Sport Exhaust M2 G87|Escape trasero con control de válvulas y sonido homologado|2450.00|4|2|2"
  "EXH-MID-006|Line-Pipe Intermedio F30 335i|Tubo intermedio de acero 304, elimina resonador|289.00|22|8|2"

  # ── Turbo ──
  "TUR-PUR-001|Pure Turbos Stage 2 S55|Par de turbos híbridos, hasta 750 CV con metanol|4290.00|2|2|3"
  "TUR-VRS-002|VRSF Charge Pipe N54 335i|Tubería de carga de aluminio con acople reforzado|219.00|26|8|3"
  "TUR-TIA-003|TTE850 Turbo Upgrade B58|Turbo híbrido de cartucho grande para B58 Gen2|3980.00|2|1|3"
  "TUR-BOV-004|Válvula Blow-Off Tial Q N54|Válvula de descarga 50 mm con brida de aluminio|389.00|11|5|3"
  "TUR-WGA-005|Actuadores Wastegate N54 Reforzados|Par de actuadores de 18 psi, resorte de acero|264.00|16|6|3"
  "TUR-BPP-006|Boost Pipe Kit S55 M3/M4|Tuberías de carga de aluminio, sustituyen las de plástico|649.00|8|4|3"

  # ── Intercooler y refrigeración ──
  "COO-CSF-001|CSF Intercooler Race S55|Intercooler de barra y placa con -35 grados de IAT|1590.00|6|3|4"
  "COO-WAG-002|Wagner Tuning EVO2 N55|Intercooler competición con núcleo de 100 mm|1120.00|9|4|4"
  "COO-MIS-003|Mishimoto Radiador Aluminio E46 M3|Radiador de doble paso, 40% más capacidad|649.00|7|3|4"
  "COO-OIL-004|Cooler de Aceite B58 con Termostato|Kit de refrigeración de aceite con sándwich y latiguillos|879.00|5|3|4"
  "COO-CSF-005|CSF Radiador Auxiliar F8X M3|Radiador lateral de alto flujo para circuito|540.00|10|4|4"

  # ── Suspensión ──
  "SUS-KWV-001|KW Variant 3 Coilovers F80 M3|Suspensión roscada con ajuste de compresión y rebote|3450.00|4|2|5"
  "SUS-BIL-002|Bilstein B16 DampTronic G80 M3|Roscada con control electrónico integrado|3890.00|3|2|5"
  "SUS-OHL-003|Ohlins Road & Track E9X M3|Roscada DFV con reglaje único de 20 clics|3190.00|3|2|5"
  "SUS-SWY-004|Barras Estabilizadoras Eibach F30|Juego delantera y trasera, tres posiciones|689.00|12|5|5"
  "SUS-CMB-005|Brazos de Camber Ajustables E92|Par trasero con rótulas esféricas|420.00|18|6|5"
  "SUS-STB-006|Refuerzo de Torreta Delantero F82|Barra de aluminio con anclaje de tres puntos|349.00|14|5|5"
  "SUS-BUS-007|Casquillos Poliuretano Trasero E46|Kit completo de silentblocks del subchasis|275.00|20|8|5"

  # ── Frenos ──
  "BRK-BRE-001|Brembo GT 6 Pistones 380 mm F8X|Kit delantero con discos ranurados y latiguillos|5490.00|3|2|6"
  "BRK-STO-002|StopTech ST-60 E92 335i|Pinzas de seis pistones con discos de dos piezas|3290.00|4|2|6"
  "BRK-PAD-003|Pastillas Ferodo DS2500 M3 F80|Juego delantero para uso mixto calle y circuito|389.00|25|8|6"
  "BRK-DSC-004|Discos Perforados Zimmermann G20|Par delantero de 348 mm con tratamiento anticorrosión|540.00|16|6|6"
  "BRK-LIN-005|Latiguillos Metálicos Goodridge|Juego completo de cuatro latiguillos homologados|179.00|30|10|6"
  "BRK-FLU-006|Líquido de Frenos Motul RBF660|Punto de ebullición 325 grados, envase de 500 ml|24.90|60|20|6"

  # ── Software ──
  "ECU-BM3-001|Bootmod3 Licencia S55 M3/M4|Reprogramación flasheable con mapas Stage 1 y 2|699.00|35|10|7"
  "ECU-MHD-002|MHD Wireless Flasher N54/N55|Interfaz OBD y licencia de aplicación|499.00|28|10|7"
  "ECU-JB4-003|Burger JB4 Piggyback B58|Módulo con seis mapas y conexión Bluetooth|529.00|20|8|7"
  "ECU-DIN-004|Dinan DinanTronics Stage 3 B58|Módulo con garantía de concesionario|1290.00|6|3|7"
  "ECU-CAN-005|CAN Gateway para Datalogging|Registro de parámetros en circuito, salida CSV|349.00|12|5|7"

  # ── Transmisión ──
  "DRV-CLU-001|Embrague Sachs Performance N54|Kit reforzado para 600 Nm con volante aligerado|1490.00|5|3|8"
  "DRV-FLY-002|Volante Bimasa Aligerado E46 M3|Volante de acero de 7,5 kg con corona nueva|980.00|6|3|8"
  "DRV-MNT-003|Soportes de Motor Reforzados F8X|Par de soportes de poliuretano 75A|429.00|15|6|8"
  "DRV-DIF-004|Diferencial Autoblocante Wavetrac|Bloqueo helicoidal para carcasa de 210 mm|2190.00|3|2|8"
  "DRV-SHF-005|Kit Palanca Corta UUC E9X|Reduce el recorrido un 40% con casquillos de acero|389.00|22|8|8"
)

crear_categorias() {
  echo "==> Creando ${#CATEGORIAS[@]} categorías"
  IDS_CATEGORIA=()
  for fila in "${CATEGORIAS[@]}"; do
    IFS='|' read -r nombre descripcion <<<"$fila"
    local cuerpo
    cuerpo=$(python -c "
import json,sys
print(json.dumps({'name':sys.argv[1],'description':sys.argv[2]}))" "$nombre" "$descripcion")
    local respuesta id
    respuesta=$(api POST /categories "$cuerpo")
    id=$(printf '%s' "$respuesta" | python -c "import sys,json;print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
    if [ -z "$id" ]; then
      echo "ERROR creando la categoría '$nombre': $respuesta" >&2
      exit 1
    fi
    IDS_CATEGORIA+=("$id")
    printf '    [%s] %s\n' "$id" "$nombre"
  done
}

crear_productos() {
  echo "==> Creando ${#PRODUCTOS[@]} productos"
  local creados=0
  for fila in "${PRODUCTOS[@]}"; do
    IFS='|' read -r sku nombre descripcion precio stock minimo indice <<<"$fila"
    local id_categoria="${IDS_CATEGORIA[$((indice - 1))]}"
    local cuerpo
    cuerpo=$(python -c "
import json,sys
print(json.dumps({
  'sku': sys.argv[1], 'name': sys.argv[2], 'description': sys.argv[3],
  'price': float(sys.argv[4]), 'stock': int(sys.argv[5]),
  'minimumStock': int(sys.argv[6]), 'categoryId': int(sys.argv[7]), 'active': True}))" \
      "$sku" "$nombre" "$descripcion" "$precio" "$stock" "$minimo" "$id_categoria")
    local codigo
    codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -X POST "$BACKEND/products" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$cuerpo")
    if [ "$codigo" != "201" ]; then
      echo "ERROR creando el producto $sku: HTTP $codigo" >&2
      exit 1
    fi
    creados=$((creados + 1))
  done
  echo "    $creados productos creados"
}

# ── 3. movimientos de stock ──────────────────────────────────────────────────

# Genera historial real: entradas de proveedor, ventas y ajustes de inventario.
# Las últimas salidas dejan varias referencias por debajo de su mínimo, para que
# la pantalla de alertas y el panel de negocio tengan algo que enseñar.
MOVIMIENTOS_REGISTRADOS=0

movimiento() { # sku tipo cantidad motivo
  local sku="$1" tipo="$2" cantidad="$3" motivo="$4"
  local id
  id=$(api GET "/products?search=$sku&size=1" \
    | python -c "
import sys,json
c=json.load(sys.stdin).get('content',[])
print(c[0]['id'] if c else '')")
  if [ -z "$id" ]; then
    echo "ERROR: no se encontró el producto $sku" >&2
    exit 1
  fi
  local cuerpo codigo
  cuerpo=$(python -c "
import json,sys
print(json.dumps({'productId':int(sys.argv[1]),'type':sys.argv[2],
                  'quantity':int(sys.argv[3]),'reason':sys.argv[4]}))" \
    "$id" "$tipo" "$cantidad" "$motivo")
  codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -X POST "$BACKEND/api/stock/movements" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$cuerpo")
  if [ "$codigo" != "201" ]; then
    echo "ERROR en el movimiento $tipo de $sku: HTTP $codigo" >&2
    exit 1
  fi
  MOVIMIENTOS_REGISTRADOS=$((MOVIMIENTOS_REGISTRADOS + 1))
}

generar_movimientos() {
  echo "==> Generando historial de movimientos"

  # Entradas de proveedor
  movimiento "INT-EVN-001" IN 6 "Pedido a Eventuri UK, albarán EV-4471"
  movimiento "EXH-VRS-002" IN 10 "Reposición VRSF, contenedor Q3"
  movimiento "BRK-PAD-003" IN 20 "Compra Ferodo, lote FE-8890"
  movimiento "ECU-BM3-001" IN 15 "Licencias Bootmod3, activación por lote"
  movimiento "COO-WAG-002" IN 4 "Pedido Wagner Tuning, referencia WT-2210"
  movimiento "SUS-CMB-005" IN 12 "Reposición de brazos traseros"
  movimiento "TUR-VRS-002" IN 14 "Charge pipes, pedido trimestral"

  # Ventas e instalaciones
  movimiento "BRK-FLU-006" OUT 24 "Consumo de taller, purgas de circuito"
  movimiento "BRK-PAD-003" OUT 18 "Instalación M3 F80, cliente Ramírez"
  movimiento "ECU-MHD-002" OUT 12 "Ventas online de licencias"
  movimiento "INT-KNF-005" OUT 30 "Pedido mayorista taller asociado"
  movimiento "EXH-MID-006" OUT 16 "Instalaciones F30 de la semana"
  movimiento "TUR-VRS-002" OUT 22 "Ventas mostrador N54"
  movimiento "SUS-BUS-007" OUT 15 "Reconstrucción de subchasis E46"
  movimiento "DRV-SHF-005" OUT 16 "Kits de palanca corta, campaña de mayo"
  movimiento "BRK-LIN-005" OUT 22 "Latiguillos, montaje en cinco vehículos"
  movimiento "ECU-JB4-003" OUT 14 "Ventas JB4 para B58"
  movimiento "INT-BMC-002" OUT 32 "Pedido distribuidor, filtros S55"

  # Ajustes de inventario. ADJUSTMENT fija el stock al valor absoluto, no suma:
  # uno corrige al alza tras el recuento y otro a la baja por una unidad dañada.
  movimiento "TUR-PUR-001" ADJUSTMENT 3 "Recuento anual, aparece una unidad en tránsito"
  movimiento "EXH-AKR-001" ADJUSTMENT 2 "Inventario de vitrina, una unidad con daño de transporte"
  movimiento "COO-CSF-001" OUT 3 "Montaje S55 para circuito, cliente Peña"
  movimiento "SUS-KWV-001" OUT 3 "Instalación KW V3 en M3 F80"

  # Dos roturas de stock: alimentan el panel de stock crítico, que cuenta
  # existencias a cero, distinto de las alertas por debajo del mínimo.
  movimiento "TUR-TIA-003" OUT 2 "Ambas unidades vendidas, fabricante sin fecha de reposición"
  movimiento "DRV-DIF-004" OUT 3 "Rotura de stock, pedido a Wavetrac pendiente"

  echo "    $MOVIMIENTOS_REGISTRADOS movimientos registrados"
}

# ── 4. tráfico para los paneles ──────────────────────────────────────────────

# Los paneles de Aplicación y Seguridad miden peticiones, latencia y códigos de
# respuesta. Sin tráfico salen planos, y un dashboard vacío en la presentación
# parece que la observabilidad no funciona. Se incluyen negativos a propósito:
# 401 sin token, 401 con token inválido, 403 por scope y 404, que son los que
# alimentan el panel de Seguridad.
generar_trafico() {
  local rondas="${1:-4}"
  echo "==> Generando tráfico para los paneles ($rondas rondas)"

  local token_viewer
  token_viewer=$(curl -s --max-time 20 -X POST \
    "$KEYCLOAK/realms/${KC_REALM:-inventory}/protocol/openid-connect/token" \
    -d "grant_type=password" -d "client_id=inventory-frontend" \
    -d "username=$KC_USER_VIEWER_USERNAME" -d "password=$KC_USER_VIEWER_PASSWORD" \
    --data-urlencode "scope=$SCOPES" \
    | python -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))")

  local rutas=(
    /products /categories /api/stock/movements /api/stock/alerts
    /api/reports/dashboard-metrics /api/reports/low-stock /api/reports/best-sellers
    /api/reports/stock-summary /api/audit/all /api/audit/products /me
  )

  for _ in $(seq 1 "$rondas"); do
    for ruta in "${rutas[@]}"; do
      curl -s -o /dev/null --max-time 15 -H "Authorization: Bearer $TOKEN" "$BACKEND$ruta"
    done
    curl -s -o /dev/null --max-time 10 "$BACKEND/products"
    curl -s -o /dev/null --max-time 10 -H "Authorization: Bearer token-invalido" "$BACKEND/products"
    curl -s -o /dev/null --max-time 10 -H "Authorization: Bearer $token_viewer" "$BACKEND/api/users"
    curl -s -o /dev/null --max-time 10 -H "Authorization: Bearer $TOKEN" "$BACKEND/products/99999"
  done
  echo "    $((rondas * (${#rutas[@]} + 4))) peticiones, con 401, 403 y 404 incluidos"
}

# ── 5. resumen ───────────────────────────────────────────────────────────────

resumen() {
  echo
  echo "==> Resumen"
  local productos alertas movimientos revisiones
  productos=$(api GET "/products?size=1" | python -c "import sys,json;print(json.load(sys.stdin)['totalElements'])")
  alertas=$(api GET /api/stock/alerts | python -c "
import sys,json;d=json.load(sys.stdin);print(len(d if isinstance(d,list) else d.get('content',[])))")
  movimientos=$(sql "select count(*) from stock_movements;")
  revisiones=$(sql "select (select count(*) from products_aud)+(select count(*) from categories_aud);")
  printf '    productos:            %s\n' "$productos"
  printf '    categorías:           %s\n' "${#CATEGORIAS[@]}"
  printf '    movimientos:          %s\n' "$movimientos"
  printf '    revisiones auditadas: %s\n' "$revisiones"
  printf '    productos en alerta:  %s\n' "$alertas"
  printf '    en rotura de stock:   %s\n' \
    "$(api GET /api/reports/dashboard-metrics | python -c "import sys,json;print(json.load(sys.stdin)['criticalStockCount'])")"
  printf '    valor del inventario: %s\n' \
    "$(api GET /api/reports/dashboard-metrics | python -c "import sys,json;print(json.load(sys.stdin)['totalInventoryValue'])")"
  echo
  echo "    Frontend:  http://localhost:3000   ($KC_USER_ADMIN_USERNAME)"
  echo "    Grafana:   http://localhost:3001"
  echo "    Swagger:   $BACKEND/swagger-ui.html"
}

# ── ejecución ────────────────────────────────────────────────────────────────

esperar_backend
obtener_token
purgar
crear_categorias
crear_productos
generar_movimientos
generar_trafico "${RONDAS_TRAFICO:-4}"
resumen
