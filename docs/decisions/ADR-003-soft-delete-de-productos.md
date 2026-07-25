# ADR-003 — Los productos se desactivan, no se borran

- **Estado:** Aceptado
- **Fecha:** 2026-07-25
- **Autores:** Equipo de desarrollo
- **Revisores:** Docente

> **Numeración.** Este ADR se planificó como 002 y se renumeró a 003: el 002 quedó para el mapa rol→scopes, que se escribió antes por ser una decisión de seguridad con un hallazgo en vivo detrás.

---

## Contexto

El enunciado exige que la gestión de productos permita *"eliminar"*, y a la vez que el historial de movimientos de stock conserve seis campos por movimiento y que la auditoría con Hibernate Envers registre los cambios. Las dos cosas no se pueden cumplir a la vez con un borrado físico, por dos razones concretas del esquema:

1. **La tabla de movimientos cuelga del producto con borrado en cascada.** En [`V2__create_stock_audit_tables.sql`](../../backend/src/main/resources/db/migration/V2__create_stock_audit_tables.sql):

   ```sql
   product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE
   ```

   Un `DELETE FROM products` no falla: **se lleva por delante todos los movimientos de ese producto**, en silencio. El historial que el enunciado pide como entregable desaparecería justo para los productos que alguien decidió retirar.

2. **La auditoría de Envers registra el cambio, no la existencia.** `Product` está `@Audited`; una desactivación queda como una revisión `MOD` con el valor anterior y el nuevo, atribuible a un usuario. Un borrado físico deja una revisión `DEL` y la fila viva desaparece, así que los informes que enlazan movimiento → producto pierden el nombre y el SKU con los que se registró la operación.

---

## Decisión

**`DELETE /products/{id}` desactiva el producto (`active = false`) y devuelve 204. No existe borrado físico por API.**

La implementación está en [`ProductServiceImpl.delete`](../../backend/src/main/java/com/inventory/product/service/ProductServiceImpl.java):

```java
Product product = productRepository.findById(id).orElseThrow(() -> productNotFound(id));
product.setActive(Boolean.FALSE);
productRepository.save(product);
```

Consecuencias directas del contrato:

- Un producto inexistente responde **404**; uno ya desactivado responde **204** otra vez (la operación es idempotente).
- La **reactivación** no necesita endpoint propio: `PATCH /products/{id}` acepta `active`, con el mismo scope `product:manage`.
- Los informes que miden inventario vigente filtran `active = true` en la consulta: `findLowStockProducts`, `findCriticalStockProducts`, `countLowStockProducts` y los dos rankings de `topProducts`. Un producto desactivado no contamina alertas ni rankings.
- `dashboardMetrics` publica el conteo de inactivos como dato propio, en vez de esconderlos.

---

## Alternativas consideradas

| Alternativa | Razón de descarte |
|---|---|
| **Borrado físico (`DELETE FROM products`)** | La cascada de `stock_movements` borra el historial sin avisar. Incumple el requisito de historial y deja huecos en la auditoría |
| **Borrado físico bloqueado por FK (quitar el `ON DELETE CASCADE`)** | Protegería el historial, pero convierte "eliminar" en una operación que falla con error de integridad en cuanto el producto tiene un solo movimiento — que es el caso normal. Traslada el problema al usuario sin resolverlo |
| **Archivar en una tabla `products_archived`** | Duplica esquema y consultas para el mismo efecto que un flag. Rompe la FK de los movimientos, que apunta a `products(id)` |
| **`@SQLDelete` + `@Where` de Hibernate (soft delete transparente)** | Oculta los inactivos en **todas** las consultas de forma implícita, incluida la auditoría y los informes que sí deben verlos. Prefiere magia sobre un `WHERE active = true` explícito y revisable, y complica los tests |

---

## Consecuencias

### Positivas

- El historial de movimientos sobrevive a la retirada de un producto: el enlace movimiento → producto sigue resolviendo nombre y SKU.
- La operación es **reversible** y auditable: Envers guarda quién desactivó y cuándo, y `PATCH` permite revertirlo.
- Los informes de inventario vigente ya filtran por `active`, así que retirar un producto no falsea el valor total ni las alertas.

### Negativas / Riesgos

- **El SKU queda reservado para siempre.** `products.sku` es `NOT NULL UNIQUE` a nivel de esquema, sin condición sobre `active`. Volver a dar de alta el mismo SKU de un producto desactivado responde **409**; hay que reactivar el existente. Es el comportamiento correcto para no duplicar el histórico, pero sorprende si se espera que "eliminar" libere el código.
- **El listado no oculta los inactivos por defecto.** `GET /products` solo filtra por `active` si el cliente manda el parámetro; sin él devuelve activos e inactivos, y lo mismo hacen `findById` y `findBySku`. Es deliberado —el frontend necesita poder mostrarlos para reactivarlos— pero significa que un cliente nuevo que no filtre verá productos retirados.
- **El borrado físico sigue siendo posible fuera de la API.** Un `DELETE` por SQL directo, o un `productRepository.deleteById` que alguien añada, dispara la cascada y borra el historial. No hay nada en la base que lo impida; la protección es esta decisión y la revisión de código.
- La tabla crece de forma monótona: no hay purga ni retención definida. Con el volumen del proyecto es irrelevante, pero es una decisión aplazada, no resuelta.

---

## Referencias

- [`ProductServiceImpl.delete`](../../backend/src/main/java/com/inventory/product/service/ProductServiceImpl.java) — implementación
- [`V2__create_stock_audit_tables.sql`](../../backend/src/main/resources/db/migration/V2__create_stock_audit_tables.sql) — `ON DELETE CASCADE` que motiva la decisión
- [`V1__create_initial_schema.sql`](../../backend/src/main/resources/db/migration/V1__create_initial_schema.sql) — `sku VARCHAR(100) NOT NULL UNIQUE`
- RF-03 — Eliminar producto ([requisitos funcionales](../requisitos/requisitos-funcionales.md))
- TEST-3 del plan: ejecutar la colección Postman por primera vez destapó que esperaba `200` + cuerpo en el borrado, cuando el contrato es `204` sin cuerpo
- [ADR Template — Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
