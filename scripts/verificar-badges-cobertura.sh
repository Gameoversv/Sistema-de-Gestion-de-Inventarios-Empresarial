#!/usr/bin/env bash
#
# Lee la cobertura medida y comprueba que los badges del README digan la verdad.
#
# Por que existe: el README traia un badge `coverage-placeholder-brightgreen`,
# verde fijo, que no medía nada. Un badge estatico se queda obsoleto en cuanto la
# cobertura se mueve, y nadie lo nota. Este script convierte esa mentira silenciosa
# en un fallo de CI.
#
# No se generan SVG ni se commitean badges desde CI a proposito: `main` exige pull
# request con revision, asi que un push automatico del runner quedaria bloqueado por
# la propia proteccion de rama. El badge se actualiza a mano en el PR que mueve la
# cobertura, que es cuando toca mirarlo.
#
#   scripts/verificar-badges-cobertura.sh [backend|frontend|todo]
#
# Salida 1 si el README no coincide con lo medido.
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$RAIZ/README.md"
JACOCO_CSV="$RAIZ/backend/target/site/jacoco/jacoco.csv"
FRONT_JSON="$RAIZ/frontend/coverage/coverage-summary.json"
QUE="${1:-todo}"
fallos=0

# jacoco.csv: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,
#             BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...
pct_backend() { # pct_backend <indice_missed> <indice_covered>
  awk -F, -v m="$1" -v c="$2" 'NR>1 {miss+=$m; cov+=$c}
    END {if (miss+cov == 0) exit 1; printf "%.1f", cov*100/(miss+cov)}' "$JACOCO_CSV"
}

# Git Bash en Windows solo trae `python`; los runners de Ubuntu solo `python3`.
PY=$(command -v python3 || command -v python)

pct_frontend() {
  "$PY" -c "import json,sys;print(f\"{json.load(open(sys.argv[1]))['total']['lines']['pct']:.1f}\")" "$FRONT_JSON"
}

# El badge se da por correcto si el README contiene el numero medido. Se compara el
# texto y no el SVG porque shields.io genera la imagen a partir de esa misma cadena.
comprobar() { # comprobar <etiqueta> <valor_medido> <patron_en_readme>
  local etiqueta="$1" medido="$2" patron="$3"
  local enReadme
  enReadme=$(grep -oE "$patron" "$README" | head -1 | grep -oE '[0-9]+\.[0-9]+')
  if [ -z "$enReadme" ]; then
    echo "  ✗ $etiqueta: no encuentro el badge en README.md (patron: $patron)"
    fallos=$((fallos+1))
    return
  fi
  if [ "$enReadme" != "$medido" ]; then
    echo "  ✗ $etiqueta: el README dice ${enReadme}% y lo medido es ${medido}%"
    echo "     Actualiza el badge en README.md antes de mezclar."
    fallos=$((fallos+1))
  else
    echo "  ✓ $etiqueta: ${medido}%"
  fi
}

if [ "$QUE" = "backend" ] || [ "$QUE" = "todo" ]; then
  if [ ! -f "$JACOCO_CSV" ]; then
    echo "  ! sin $JACOCO_CSV — ejecuta ./mvnw verify en backend/ primero"
    [ "$QUE" = "backend" ] && exit 1
  else
    comprobar "backend lineas" "$(pct_backend 8 9)" 'backend%20coverage-[0-9]+\.[0-9]+'
    comprobar "backend ramas"  "$(pct_backend 6 7)" 'backend%20branches-[0-9]+\.[0-9]+'
  fi
fi

if [ "$QUE" = "frontend" ] || [ "$QUE" = "todo" ]; then
  if [ ! -f "$FRONT_JSON" ]; then
    echo "  ! sin $FRONT_JSON — ejecuta npm run test:coverage en frontend/ primero"
    [ "$QUE" = "frontend" ] && exit 1
  else
    comprobar "frontend lineas" "$(pct_frontend)" 'frontend%20coverage-[0-9]+\.[0-9]+'
  fi
fi

[ "$fallos" -gt 0 ] && exit 1
exit 0
