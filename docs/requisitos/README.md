# Documentación de Requisitos

Cubre el entregable *"Documentación de Requisitos: crear un documento detallado de requisitos funcionales y no funcionales"* del enunciado (`Proyecto_Final_V3.pdf`).

| Documento | Contenido |
|---|---|
| [requisitos-funcionales.md](requisitos-funcionales.md) | **RF-01 … RF-22** — qué hace el sistema: productos, stock, auditoría, API, dashboard y matriz de permisos |
| [requisitos-no-funcionales.md](requisitos-no-funcionales.md) | **RNF-01 … RNF-24** — cómo debe comportarse: seguridad, rendimiento, observabilidad, calidad, CI/CD, entornos y mantenibilidad |

---

## Método

Cada requisito sale de una de dos fuentes, y el documento lo declara:

- **Enunciado.** Texto literal del PDF, citado en la columna *Origen*. Es exigencia, no interpretación.
- **[criterio propio].** Decisión del equipo que el enunciado no pide. Se marca explícitamente para que nadie la confunda con un requisito evaluable.

La columna *Estado* no se rellena de memoria. Cada fila apunta al código que la implementa (`fichero:línea`) y a la prueba o informe que la verifica. Si algo está a medias, la fila dice qué falta y con qué identificador del [plan de ejecución](../PLAN_EJECUCION.md) se cierra.

| Estado | Significado |
|---|---|
| **Cumple** | Implementado y verificado por una prueba automatizada o un informe de evidencia |
| **Parcial** | Implementado incompleto, o implementado sin verificación automatizada |
| **Pendiente** | No implementado |

---

## Resumen de cumplimiento

| Bloque | Requisitos | Cumple | Parcial | Pendiente |
|---|---|---|---|---|
| **Funcionales** — productos, stock, auditoría, API, dashboard, permisos | RF-01…RF-22 | 20 | 2 | 0 |
| No funcionales — seguridad | RNF-01…RNF-07 | 5 | 1 | 1 |
| No funcionales — rendimiento y capacidad | RNF-08…RNF-10 | 1 | 1 | 1 |
| No funcionales — observabilidad | RNF-11…RNF-16 | 5 | 1 | 0 |
| No funcionales — calidad, CI/CD y entornos | RNF-17…RNF-21 | 2 | 3 | 0 |
| No funcionales — datos, operación y repositorio | RNF-22…RNF-24 | 2 | 1 | 0 |
| **Total** | **46** | **35** | **9** | **2** |

Los dos pendientes son **Policies de Keycloak** (RNF-05, decisión explícita de la Ola 8) y el **tiempo de respuesta bajo carga** (RNF-08): no hay ni una prueba de rendimiento, la única de las ocho capas de testing que está a cero.

Los parciales se concentran en el pipeline: capas de testing escritas que **el CI no ejecuta** (E2E), Testcontainers sin Keycloak y las etapas de Jenkins que no arrancan sobre Docker Desktop en Windows.

---

## Trazabilidad hacia el resto de la documentación

```
Proyecto_Final_V3.pdf          ← fuente de verdad
  └── docs/requisitos/         ← este directorio: qué debe hacer y cómo debe comportarse
        ├── docs/arquitectura/       ← cómo está construido       (pendiente)
        ├── docs/operacion/          ← cómo se opera y mantiene   (pendiente)
        └── docs/testing/            ← cómo se verifica
              ├── reportes/          ← evidencia por hallazgo (12 informes)
              └── guia-de-pruebas.md ← casos, resultados y defectos (pendiente)
```

El [plan de ejecución](../PLAN_EJECUCION.md) es el documento vivo de prioridades; este directorio es la especificación estable. Cuando discrepen, manda el PDF.

---

## Convenciones de identificador

- `RF-nn` — requisito funcional
- `RNF-nn` — requisito no funcional
- Los identificadores **no se reutilizan**. Si un requisito se retira, la fila se conserva marcada como retirada, para que las referencias desde issues y PRs no queden colgando.
- Las referencias a trabajo pendiente usan el identificador del plan de ejecución (`T-3`, `G-1`, `A-2`…), no un `RF`/`RNF` nuevo.
