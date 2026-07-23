# Guía de Contribución

Gracias por contribuir al proyecto. Por favor lee estas guías antes de abrir un issue o PR.

---

## Flujo de Trabajo

1. Crea una rama desde `main` siguiendo la convención de nombres
2. Implementa los cambios con commits que sigan Conventional Commits
3. Asegúrate de que todos los tests pasen localmente
4. Abre un Pull Request hacia `main`
5. Espera revisión y aprobación (mínimo 1 reviewer)

> `main` es la única rama de integración. Las ramas `develop` y `develop-rebuilt`
> quedaron abandonadas y no deben usarse como base ni como destino de PRs.

## Convención de Nombres de Ramas

Se usan los mismos prefijos que los tipos de Conventional Commits, para que el nombre
de la rama y el de sus commits no digan cosas distintas.

```
feat/<descripcion-corta>        # Nueva funcionalidad
fix/<descripcion-corta>         # Corrección de bug
docs/<descripcion-corta>        # Documentación y evidencia
test/<descripcion-corta>        # Pruebas
ci/<descripcion-corta>          # Pipelines
chore/<descripcion-corta>       # Mantenimiento, config, refactor
```

Ejemplos reales del historial:
- `feat/obs-4-loki-logs`
- `fix/g6-evidencia-y-openapi`
- `test/ci-verify-blocking`
- `chore/ola-0-quick-wins`

## Conventional Commits

Los mensajes de commit deben seguir el estándar [Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>(<scope opcional>): <descripción corta>

[cuerpo opcional]

[footer opcional: BREAKING CHANGE / refs #issue]
```

### Tipos permitidos

| Tipo | Uso |
|------|-----|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `docs` | Solo documentación |
| `style` | Formato, sin cambios de lógica |
| `refactor` | Refactoring sin feat ni fix |
| `test` | Agregar o corregir tests |
| `chore` | Mantenimiento, herramientas, build |
| `perf` | Mejora de rendimiento |
| `ci` | Cambios en CI/CD |
| `revert` | Revertir commit anterior |

### Ejemplos

```
feat(inventario): agregar endpoint de búsqueda por código
fix(auth): corregir validación de token JWT expirado
docs: actualizar guía de instalación en README
test(stock): agregar tests de integración para movimientos
chore: actualizar dependencias de Spring Boot a 3.2.5
```

## Pull Requests

- El PR debe apuntar a `main`
- El título debe seguir Conventional Commits
- Completa el template de PR con descripción, tipo de cambio y checklist
- Al menos 1 aprobación requerida antes de merge
- Los status checks de CI deben pasar

## Issues

Usa las plantillas disponibles:
- **Bug Report** — para reportar errores
- **Feature Request** — para proponer funcionalidades
- **Task** — para tareas técnicas o de mantenimiento

## Setup Local

```bash
# Pre-requisitos
# - Java 21+
# - Node.js 20+
# - Docker & Docker Compose

cp .env.example .env
docker compose up -d
```

## Estándares de Código

- **Cobertura mínima:** 80% por módulo
- **Estilo Java:** Google Java Style Guide + Checkstyle
- **Estilo TypeScript/React:** ESLint + Prettier config del proyecto
- **No commits directos a `main`** — todo cambio entra por PR revisado

## Preguntas

Abre un issue con la etiqueta `question` o contacta al equipo.
