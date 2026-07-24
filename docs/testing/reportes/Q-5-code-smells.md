# Q-5 — Los 16 code smells de SonarCloud

**Fecha:** 2026-07-23
**Origen:** API de SonarCloud sobre `main`, no la interfaz — los 16 se extrajeron con `api/issues/search` para trabajar sobre la lista real y no sobre una captura
**Estado:** los 16 resueltos, 288 tests en verde

---

## Reparto

| Dónde | Cantidad |
|---|---|
| Código de producción | 3 |
| Tests | 13 |

Ninguno era un bug: SonarCloud reportaba 0 bugs y 0 vulnerabilidades. Es deuda de mantenibilidad que nadie había medido porque Sonar no se ejecutaba hasta Q-1.

## Código de producción

**`ProductServiceImpl` — `"Product not found: "` escrito en cuatro sitios.** Sustituido por un método `productNotFound(Long)` que devuelve la excepción. No es solo estética: una búsqueda futura por ese texto —en logs, en tests, en un informe de incidencia— encontraba solo algunas de las rutas que lo emiten.

**`ReportServiceImpl` — `"Sin categoría"` en tres.** El literal venía además dentro de la misma ternaria repetida tres veces. Se extrajo `categoryNameOf(Product)`, que mata las dos duplicaciones a la vez. Si mañana cambia la etiqueta, no quedan dos informes diciendo una cosa y el tercero otra.

**`UnifiedAuditService` — `catch (LazyInitializationException ignored) {}` sin explicar.** El bloque vacío era deliberado y ahora lo dice: el resumen es informativo y se construye fuera de la transacción que cargó el movimiento; si el producto llega como proxy sin sesión, se emite el resumen sin esa parte en vez de tumbar la consulta de auditoría por un dato accesorio. Un `catch` vacío sin comentario es indistinguible de un error, y ese es justo el punto de la regla.

## Tests

### Los dos que dejaron de ser cosméticos

**`StockServiceConcurrencyIT` — una lista de `Future` que se llenaba y nunca se leía.** Parecía ruido. No lo era: una excepción distinta de `BusinessException` moría dentro de su `Future` y el test fallaba más tarde en el invariante 3, con un descuadre de contadores y sin rastro de la causa real. Ahora los `Future` se consumen tras el latch, así que un fallo inesperado sale con su traza. Es el único de los 16 que mejora la capacidad de diagnóstico del proyecto.

**`ExceptionTriggerController.validate()` — método vacío.** El vacío es correcto: lo que se prueba es `@Valid`, que rechaza el cuerpo antes de llegar al método. Documentado, porque sin el comentario parece una implementación a medias.

### El falso positivo

**`StockServiceTest:236` — supuesto comentario `TODO` pendiente.** No había ningún TODO en todo `src/`. La causa es el idioma:

```java
// Verifica que todo movimiento confirmado publica StockMovementRecordedEvent, ...
```

La regla S1135 busca el tag `TODO` sin distinguir mayúsculas, y la palabra española **todo** lo dispara. Se reformuló a *"cada movimiento"*. La alternativa —marcarlo como falso positivo en Sonar— habría dejado la trampa puesta para el próximo comentario en español que empiece igual.

### El resto

| Smell | Cantidad | Corrección |
|---|---|---|
| `isEqualTo(0)` en vez de `isZero()` | 5 | Sustituido |
| Lambda de `assertThatThrownBy` con más de una invocación que puede lanzar | 3 | Argumentos sacados fuera de la lambda; el sujeto del test no cambia |
| Variable local sin usar (`Fixture fixture = new Fixture(factory)`) | 1 | El constructor tiene efecto —arma el mock estático— y la referencia no se usaba: se conserva la llamada sin asignar |
| Tres tests que solo se diferencian en un argumento | 1 | `@ParameterizedTest` con `@NullSource` y `@ValueSource` |

Sobre el parametrizado: los tres casos de `findAll` —sin filtro, con cadena en blanco y con texto— comprobaban lo mismo con distinta entrada. Quedan como un solo test con tres invocaciones, así que el recuento total no baja y cada caso sigue apareciendo con su nombre en el informe.

### Un arreglo a medias que el propio Sonar cazó

Los tres casos de `assertThatThrownBy` se corrigieron primero sacando solo la construcción del request:

```java
StockMovementRequest request = new StockMovementRequest(1L, MovementType.OUT, 5, null, null);
assertThatThrownBy(() -> stockService.registerMovement(request, jwt("bob")))
```

El análisis del PR volvió a marcar dos de los tres: `jwt("bob")` seguía dentro de la lambda y también es una invocación que puede lanzar. Si `jwt()` fallara, `assertThatThrownBy` capturaría **esa** excepción y el test pasaría por el motivo equivocado — que es exactamente lo que la regla S5778 previene.

Corregido sacando también el token. Vale la pena anotarlo: el arreglo intuitivo dejaba la mitad del problema en pie, y solo se vio porque el gate analiza cada PR.

## Verificación

```
Tests run: 288, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Con `spotless` activo en `validate`, es decir, comprobando formato en la misma ejecución.

`StockServiceConcurrencyIT` no se ejecuta en local —Testcontainers no arranca sobre Docker Desktop en Windows, ver #49— pero sí compila aquí y corre en el job de integración de CI, que es donde se valida el cambio de los `Future`.

## Lo que queda del área de Calidad

Nada medido. Con los 16 resueltos, SonarCloud queda en 0 bugs, 0 vulnerabilidades, 0 code smells y 0 % de duplicación. Lo que no cubre esta tarea es que el quality gate no falla por code smells nuevos: hoy los cuenta e informa, pero no bloquea.
