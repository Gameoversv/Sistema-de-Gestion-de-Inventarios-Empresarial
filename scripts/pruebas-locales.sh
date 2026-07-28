#!/usr/bin/env bash
#
# Corre las pruebas en local y enseña la cobertura en la terminal.
#
#   scripts/pruebas-locales.sh              # backend + frontend
#   scripts/pruebas-locales.sh backend
#   scripts/pruebas-locales.sh frontend
#   scripts/pruebas-locales.sh e2e          # necesita el stack levantado
#   scripts/pruebas-locales.sh --abrir      # además abre los informes HTML
#
# Por qué no basta `./mvnw test`: el goal `report` de JaCoCo está atado a la
# fase `verify` (backend/pom.xml), así que `test` a secas ejecuta las pruebas
# pero no genera el informe. Aquí se invoca `jacoco:report` explícitamente.
#
# Aviso sobre Windows: `./mvnw verify` no corre en local porque Testcontainers
# no arranca sobre Docker Desktop (issue #49). Por eso la cobertura que mide
# este script es SOLO la de los tests unitarios y sale más baja que la de CI,
# que sí ejecuta los IT. La cifra de los badges es la de CI.

set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# La consola de Windows usa cp1252 y python emite UTF-8: sin esto, cualquier
# acento en la salida de los bloques python sale como carácter de reemplazo.
export PYTHONIOENCODING=utf-8

QUE="todo"
ABRIR=0
for arg in "$@"; do
  case "$arg" in
    --abrir) ABRIR=1 ;;
    backend | frontend | e2e | todo) QUE="$arg" ;;
    *) echo "Uso: $0 [backend|frontend|e2e|todo] [--abrir]" >&2; exit 2 ;;
  esac
done

titulo() { printf '\n\033[1m%s\033[0m\n' "$1"; }
abrir() { [ "$ABRIR" = 1 ] || return 0
  command -v explorer.exe >/dev/null 2>&1 && explorer.exe "$(cygpath -w "$1" 2>/dev/null || echo "$1")" 2>/dev/null || true
}

# ── backend ──────────────────────────────────────────────────────────────────

backend() {
  titulo "Backend — tests unitarios + JaCoCo"
  ( cd backend && ./mvnw test jacoco:report -B -q )
  local salida=$?

  local csv="backend/target/site/jacoco/jacoco.csv"
  if [ ! -f "$csv" ]; then
    echo "  ERROR: no se generó $csv" >&2
    return 1
  fi

  # jacoco.csv: 4/5 instrucciones, 6/7 ramas, 8/9 líneas, 12/13 métodos
  awk -F, 'NR>1 {im+=$4; ic+=$5; bm+=$6; bc+=$7; lm+=$8; lc+=$9; mm+=$12; mc+=$13}
    END {
      printf "\n  %-14s %7s %10s\n", "métrica", "%", "cubierto"
      printf "  %-14s %6.1f%% %6d/%d\n", "líneas",       lc*100/(lc+lm), lc, lc+lm
      printf "  %-14s %6.1f%% %6d/%d\n", "ramas",        bc*100/(bc+bm), bc, bc+bm
      printf "  %-14s %6.1f%% %6d/%d\n", "métodos",      mc*100/(mc+mm), mc, mc+mm
      printf "  %-14s %6.1f%% %6d/%d\n", "instrucciones", ic*100/(ic+im), ic, ic+im
    }' "$csv"

  # Conteo de pruebas desde los informes de Surefire
  python - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
files = glob.glob('backend/target/surefire-reports/*.xml')
for fn in files:
    r = ET.parse(fn).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0))
    e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
estado = 'TODO EN VERDE' if f == e == 0 else 'CON FALLOS'
print(f"\n  {t} pruebas en {len(files)} clases | {f} fallos, {e} errores, {s} saltadas  [{estado}]")
PY

  echo "  informe: backend/target/site/jacoco/index.html"

  # Las 5 clases con menos cobertura de líneas, que es donde conviene mirar
  echo
  echo "  Clases con menos cobertura de líneas:"
  awk -F, 'NR>1 && ($8+$9)>0 {printf "    %-46s %5.1f%%  (%d/%d)\n", $3, $9*100/($9+$8), $9, $9+$8}' "$csv" \
    | sort -k2 -n | head -5

  abrir "$RAIZ/backend/target/site/jacoco/index.html"
  return $salida
}

# ── frontend ─────────────────────────────────────────────────────────────────

frontend() {
  titulo "Frontend — vitest + cobertura"
  ( cd frontend && npm run test:coverage --silent )
  local salida=$?

  local resumen="frontend/coverage/coverage-summary.json"
  if [ -f "$resumen" ]; then
    python - <<'PY'
import json
d = json.load(open('frontend/coverage/coverage-summary.json', encoding='utf-8'))['total']
print()
print(f"  {'métrica':<14} {'%':>7} {'cubierto':>10}")
for k in ('lines', 'branches', 'functions', 'statements'):
    v = d[k]
    print(f"  {k:<14} {v['pct']:>6.1f}% {v['covered']:>6}/{v['total']}")
PY
    echo "  informe: frontend/coverage/index.html"
    abrir "$RAIZ/frontend/coverage/index.html"
  else
    echo "  aviso: no se generó coverage-summary.json" >&2
  fi
  return $salida
}

# ── e2e ──────────────────────────────────────────────────────────────────────

e2e() {
  titulo "E2E — Playwright (requiere el stack levantado)"
  if ! curl -sf --max-time 5 http://localhost:3000 >/dev/null 2>&1; then
    echo "  El frontend no responde en http://localhost:3000." >&2
    echo "  Levanta el stack primero:  docker compose up -d" >&2
    return 1
  fi
  ( cd e2e && npx playwright test )
  local salida=$?
  echo "  informe: e2e/playwright-report/index.html   (npm --prefix e2e run test:report)"
  abrir "$RAIZ/e2e/playwright-report/index.html"
  return $salida
}

# ── ejecución ────────────────────────────────────────────────────────────────

fallos=0
case "$QUE" in
  backend)  backend  || fallos=1 ;;
  frontend) frontend || fallos=1 ;;
  e2e)      e2e      || fallos=1 ;;
  todo)     backend || fallos=1; frontend || fallos=1 ;;
esac

# La advertencia solo aplica al backend: es su suite la que tiene *IT.
if [ "$QUE" = "backend" ] || [ "$QUE" = "todo" ]; then
titulo "Nota sobre la diferencia con CI"
cat <<'TXT'
  Esta cobertura es solo de los tests unitarios. Los *IT necesitan
  Testcontainers, que no arranca sobre Docker Desktop en Windows (#49), así que
  `./mvnw verify` falla en local. En los runners Linux de CI sí corren, y por eso
  la cobertura de los badges es más alta que la que sale aquí.

  Para ver la cifra buena:  gh run list --workflow=ci.yml --limit 1
TXT
fi

exit $fallos
