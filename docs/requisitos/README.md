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
| No funcionales — seguridad | RNF-01…RNF-07 | 7 | 0 | 0 |
| No funcionales — rendimiento y capacidad | RNF-08…RNF-10 | 3 | 0 | 0 |
| No funcionales — observabilidad | RNF-11…RNF-16 | 6 | 0 | 0 |
| No funcionales — calidad, CI/CD y entornos | RNF-17…RNF-21 | 3 | 2 | 0 |
| No funcionales — datos, operación y repositorio | RNF-22…RNF-24 | 2 | 1 | 0 |
| **Total** | **46** | **41** | **5** | **0** |

**Ningún requisito queda pendiente.** Los dos que lo estaban se cerraron: **Policies de Keycloak** (RNF-05) se implementó en G-1 —5 Resources, 4 Policies y 7 Permissions, con la matriz de 28 decisiones verificada contra un Keycloak real— y el **tiempo de respuesta bajo carga** (RNF-08) resultó ser documentación desfasada: T-3 llevaba tiempo verde con `p(95) = 7,92 ms`.

Los cinco parciales que quedan **no son trabajo olvidado, son límites conocidos**:

| Requisito | Qué falta | Por qué |
|---|---|---|
| RF-21, RF-22 | Gestión de usuarios por API | A-2 diferido (issue #48): `user:manage` no protege ningún endpoint |
| RNF-19 | Etapas de Jenkins más allá de `Integration Tests` | Testcontainers no arranca sobre Docker Desktop en Windows; hace falta un agente Linux (issue #49). En Actions las 10 etapas corren |
| RNF-21 | Ejecutar `production.yml` | CI-2: disparar un release `v1.0.0` es una decisión explícita |
| RNF-24 | Revisión cruzada en todos los PR | Evaluable; depende de los dos integrantes, no del código |

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
