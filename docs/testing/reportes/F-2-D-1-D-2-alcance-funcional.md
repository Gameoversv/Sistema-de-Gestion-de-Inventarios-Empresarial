# F-2, D-1 y D-2 — El alcance funcional que faltaba

**Fecha:** 2026-07-23
**Entorno:** stack local, backend reconstruido, perfil `demo`
**Estado:** implementado y verificado en vivo

---

## Por qué estos tres

Son los últimos puntos del **alcance funcional** que el enunciado enumera y el sistema no cumplía:

- *"visualizar con paginación, búsqueda, filtros y **ordenamiento**"* → F-2
- *"Dashboard: productos críticos, **más vendidos**, historial reciente, métricas, indicadores"* → D-1 y D-2

La exploración previa cambió el reparto de trabajo respecto a lo que el plan suponía.

| # | Se creía | Lo que había realmente |
|---|---|---|
| F-2 | backend + frontend | El backend **ya** aceptaba `sort` vía `Pageable`. El hook lo tenía fijado a `'name'` |
| D-1 | agregar un panel | El panel existía pero mostraba **otra cosa** |
| D-2 | backend + frontend | `/critical-stock` **ya** existía; el dashboard solo pintaba el contador |

## D-1 — El ranking del dashboard no era de ventas

El dashboard pedía `/api/reports/top-products?metric=value`, que ordena por **precio × stock**. Eso mide lo que hay guardado en el almacén, no lo que sale por la puerta: un producto caro que no se vende nunca encabezaba el ranking.

El rótulo era honesto —*"Top 8 — valor de inventario"*—, así que no engañaba a nadie. Pero el enunciado pide **más vendidos**, y eso solo se puede saber agregando los movimientos `OUT`.

### Endpoint nuevo, no un `metric` más

Se descartó añadir `metric=sold` a `/top-products`. El DTO de ese endpoint lleva stock, precio y valor de inventario; un ranking de ventas los tendría todos vacíos o irrelevantes, y habría que declarar `unitsSold` como campo opcional que solo se rellena a veces.

`/api/reports/best-sellers` tiene su propio DTO con lo único que importa aquí: unidades vendidas y número de movimientos. **Fuente de datos distinta → endpoint distinto.** Además no toca el contrato de `/top-products`, que ya consume la página de Reportes.

### La agregación va en la base

```sql
SELECT new BestSellerDto(p.id, p.sku, p.name, SUM(m.quantity), COUNT(m))
FROM StockMovement m JOIN m.product p
WHERE m.type = OUT
GROUP BY p.id, p.sku, p.name
ORDER BY SUM(m.quantity) DESC
```

El equivalente en Java tendría que traerse la tabla entera de movimientos —que crece sin techo, a diferencia de la de productos— para quedarse con diez filas.

**Solo cuenta `OUT`.** Un `ADJUSTMENT` negativo corrige inventario, no es una venta: contarlo inflaría el ranking con mermas y correcciones de recuento.

## D-2 — Un número rojo no dice cuál

El dashboard mostraba `criticalStockCount`: un número. El enunciado pide **listarlos**. Un contador dice que hay un problema; la lista dice cuál producto y desde qué mínimo.

El endpoint ya existía, así que fue trabajo de frontend. Y al conectarlo salió un defecto:

```ts
export interface CriticalStockResponse {
  generatedAt: string          // no existe en el backend
  products: ProductResponse[]  // el backend manda LowStockItemDto[]
}
```

`ReportsPage` usaba `key={p.id}`, y `LowStockItemDto` no tiene `id` sino `productId`. Todas las `key` llegaban `undefined`. El tipo mal declarado impedía que TypeScript lo detectara: la única herramienta que podía cazarlo estaba desactivada por la propia declaración. Corregido el tipo y la `key` en ambas páginas.

## F-2 — El backend ya sabía; el hook no preguntaba

`ProductController` declara `@PageableDefault(size = 20, sort = "name")`, así que Spring Data aceptaba `?sort=` desde el principio. `useProducts` lo fijaba a `'name'` en cada petición.

Frontend: componente `SortableHeader` para SKU, nombre, precio y stock. Tres decisiones:

- **`aria-sort` va en el `th`, no en el botón.** Es donde los lectores de pantalla lo buscan.
- **La flecha solo aparece en la columna activa**; las demás muestran el icono neutro en gris claro, que anuncia que se puede ordenar sin competir con el indicador real.
- **Reordenar vuelve a la página 0.** Quedarse en la página 3 de un orden nuevo muestra un tramo arbitrario del listado y parece que la ordenación no ha hecho nada.

Categoría se queda sin ordenar: es una relación y ordenar por `category.name` exige un join que este cambio no necesitaba.

### El defecto que F-2 destapó

Con la ordenación expuesta en la interfaz, `sort` pasa a ser un parámetro que el usuario controla. Y un campo inexistente daba **500**:

```
GET /products?sort=noExiste,asc  →  500
PropertyReferenceException: No property 'noExiste' found for type 'Product'
```

Era preexistente —el endpoint siempre aceptó `sort`—, pero hasta ahora ningún camino de la interfaz llegaba ahí. Un parámetro de consulta que tumba el servidor con un 500 es entrada no validada.

Añadido `@ExceptionHandler(PropertyReferenceException.class)` → **400** con `ProblemDetail`, con su test escrito antes (falló con `expected:<400> but was:<500>`).

## Verificación en vivo

Todo contra el contenedor reconstruido.

**D-1 — `/api/reports/best-sellers?limit=5`:**

```json
{"limit":5,"count":5,"products":[
  {"id":24,"sku":"P2-LAP-001","name":"Laptop Pro 14","unitsSold":36,"movementCount":3},
  {"id":26,"sku":"P2-TEC-003","name":"Teclado mecanico","unitsSold":28,"movementCount":5},
  {"id":25,"sku":"P2-MON-002","name":"Monitor 27 4K","unitsSold":18,"movementCount":3}]}
```

Orden descendente correcto. Sin `report:view` responde **403**.

**F-2 — ordenación real, no simulada:**

```
sort=price,desc -> 2000.00  2000.00  1299.99  349.50
sort=price,asc  ->    9.99    59.00    89.90    99.99
sort=stock,desc ->      19       14        7       4
```

**El 500 corregido:**

```
GET /products?sort=noExiste,asc  →  400   (antes: 500)
GET /products?sort=price,desc    →  200
```

**Suites:** 294 tests de backend y 15 de frontend en verde. `tsc -b`, `eslint` y `npm run build` limpios.

## Lo que no cubre

Los tres cambios de interfaz no tienen test de frontend: la cobertura baja de 7,5 % a 7,1 % porque entra código nuevo sin pruebas. Es el hueco que cierra **C-1 + TEST-7** (Playwright en CI), donde este tipo de comportamiento —pulsar una cabecera y ver el orden cambiar— se prueba de verdad. Un test de unidad sobre `SortableHeader` comprobaría que el `onClick` llama al handler, que es casi tautológico.

La verificación de que el gráfico y el panel de críticos **pintan** con datos reales queda para el ensayo de P-3 (charter #60).
