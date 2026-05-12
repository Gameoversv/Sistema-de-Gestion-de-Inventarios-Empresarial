# Guía de Contribución

Gracias por contribuir al proyecto. Por favor lee estas guías antes de abrir un issue o PR.

---

## Flujo de Trabajo

1. Crea una rama desde `develop` siguiendo la convención de nombres
2. Implementa los cambios con commits que sigan Conventional Commits
3. Asegúrate de que todos los tests pasen localmente
4. Abre un Pull Request hacia `develop`
5. Espera revisión y aprobación (mínimo 1 reviewer)

## Convención de Nombres de Ramas

```
feature/<descripcion-corta>     # Nueva funcionalidad
bugfix/<descripcion-corta>      # Corrección de bug
hotfix/<descripcion-corta>      # Parche urgente en producción
chore/<descripcion-corta>       # Mantenimiento, config, refactor
```

Ejemplos:
- `feature/gestion-proveedores`
- `bugfix/calculo-stock-incorrecto`
- `hotfix/seguridad-token-jwt`
- `chore/actualizar-dependencias`

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

- El PR debe apuntar a `develop`, no a `main`
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
- **No commits directos a `main` ni `develop`**

## Preguntas

Abre un issue con la etiqueta `question` o contacta al equipo.
