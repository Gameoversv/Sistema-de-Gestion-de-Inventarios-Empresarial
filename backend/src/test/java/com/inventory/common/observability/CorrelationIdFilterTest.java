package com.inventory.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  @DisplayName("genera un correlationId cuando la petición no trae la cabecera")
  void generatesCorrelationIdWhenHeaderAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] captured = captureMdcDuringChain(request, response);

    assertThat(captured[0]).isNotBlank();
    assertThat(UUID.fromString(captured[0])).isNotNull();
    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .isEqualTo(captured[0]);
  }

  @Test
  @DisplayName("reutiliza el correlationId que envía el cliente")
  void reusesIncomingCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "frontend-abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] captured = captureMdcDuringChain(request, response);

    assertThat(captured[0]).isEqualTo("frontend-abc-123");
  }

  @Test
  @DisplayName("descarta una cabecera con caracteres de control y genera uno propio")
  void rejectsUnsafeCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc\n\"level\":\"ERROR\"");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] captured = captureMdcDuringChain(request, response);

    assertThat(captured[0]).doesNotContain("\n").doesNotContain("\"");
    assertThat(UUID.fromString(captured[0])).isNotNull();
  }

  @Test
  @DisplayName("registra el endpoint como método más ruta")
  void putsMethodAndPathInMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stock/movements");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] captured = captureMdcDuringChain(request, response);

    assertThat(captured[1]).isEqualTo("POST /api/stock/movements");
  }

  @Test
  @DisplayName("limpia el MDC al terminar, aunque la cadena lance")
  void clearsMdcEvenWhenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MDC.put(AuthenticatedUserMdcFilter.MDC_USER, "inv_admin");

    FilterChain failing =
        (req, res) -> {
          throw new IllegalStateException("fallo aguas abajo");
        };

    try {
      filter.doFilter(request, response, failing);
    } catch (Exception expected) {
      // el filtro no traga la excepción: solo garantiza la limpieza del MDC
    }

    assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)).isNull();
    assertThat(MDC.get(CorrelationIdFilter.MDC_ENDPOINT)).isNull();
    assertThat(MDC.get(AuthenticatedUserMdcFilter.MDC_USER)).isNull();
  }

  /**
   * Devuelve {correlationId, endpoint} tal como los ve la cadena, antes de que el filtro limpie.
   */
  private String[] captureMdcDuringChain(
      MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
    String[] captured = new String[2];
    FilterChain chain =
        (req, res) -> {
          captured[0] = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
          captured[1] = MDC.get(CorrelationIdFilter.MDC_ENDPOINT);
        };

    filter.doFilter(request, response, chain);
    return captured;
  }
}
