package com.inventory.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Verifica la señal de <em>errores distribuidos</em> que el enunciado exige en el apartado de
 * trazas, y que era la única de las cuatro sin cubrir.
 *
 * <p>El problema que resuelve no es evidente: {@link GlobalExceptionHandler} captura la excepción y
 * devuelve un {@code ResponseEntity}, así que para la instrumentación automática la petición
 * termina con normalidad. El span quedaba con estado UNSET y sin evento {@code exception}, y un 500
 * era invisible en Tempo pese a estar en los logs.
 *
 * <p>Se usa un exportador en memoria escrito aquí mismo en vez de {@code opentelemetry-sdk-testing}
 * para no añadir una dependencia por un solo test.
 */
class GlobalExceptionHandlerTracingTest {

  private final List<SpanData> exported = new ArrayList<>();
  private SdkTracerProvider tracerProvider;
  private Tracer tracer;

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @BeforeEach
  void setUp() {
    SpanExporter collector =
        new SpanExporter() {
          @Override
          public CompletableResultCode export(Collection<SpanData> spans) {
            exported.addAll(spans);
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        };
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(collector)).build();
    tracer = tracerProvider.get("test");
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  @DisplayName("un 500 marca el span como ERROR y registra la excepción")
  void genericError_marksSpanAsError() {
    // Arrange
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
    IllegalStateException boom = new IllegalStateException("base de datos caída");
    Span span = tracer.spanBuilder("GET /products").startSpan();

    // Act
    try (Scope ignored = span.makeCurrent()) {
      var response = handler.handleGeneric(boom, request);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      span.end();
    }

    // Assert
    assertThat(exported).hasSize(1);
    SpanData data = exported.get(0);
    assertThat(data.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(data.getEvents())
        .as("el span debe llevar el evento exception para ser consultable en Tempo")
        .anySatisfy(
            event -> {
              assertThat(event.getName()).isEqualTo("exception");
              assertThat(event.getAttributes().asMap().toString())
                  .contains("IllegalStateException")
                  .contains("base de datos caída");
            });
  }

  @Test
  @DisplayName("un 4xx NO marca el span como ERROR")
  void clientError_leavesSpanUnset() {
    // Un conflicto de negocio es error del cliente. Marcarlo como ERROR inflaría la tasa de
    // error de los dashboards con algo que no es un fallo del sistema.
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
    Span span = tracer.spanBuilder("POST /products").startSpan();

    try (Scope ignored = span.makeCurrent()) {
      handler.handleConflict(new ConflictException("SKU duplicado"), request);
    } finally {
      span.end();
    }

    assertThat(exported).hasSize(1);
    assertThat(exported.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    assertThat(exported.get(0).getEvents()).isEmpty();
  }
}
