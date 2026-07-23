package com.inventory.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puebla el MDC con {@code correlationId} y {@code endpoint} al principio de cada petición.
 *
 * <p>Se registra con la máxima precedencia, por delante de la cadena de Spring Security, para que
 * también las respuestas 401 y 403 —que nunca llegan a un controlador— salgan con contexto. El
 * traceId y el spanId los propaga Micrometer Tracing por su cuenta; el usuario lo añade después
 * {@link AuthenticatedUserMdcFilter}, cuando ya existe autenticación.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String MDC_CORRELATION_ID = "correlationId";
  public static final String MDC_ENDPOINT = "endpoint";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String correlationId = resolveCorrelationId(request);

    MDC.put(MDC_CORRELATION_ID, correlationId);
    MDC.put(MDC_ENDPOINT, request.getMethod() + " " + request.getRequestURI());
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    try {
      chain.doFilter(request, response);
    } finally {
      // El hilo vuelve al pool: dejar claves colgando contaminaría la siguiente
      // petición que lo reutilice.
      MDC.remove(MDC_CORRELATION_ID);
      MDC.remove(MDC_ENDPOINT);
      MDC.remove(AuthenticatedUserMdcFilter.MDC_USER);
    }
  }

  // Solo caracteres seguros de identificador: la cabecera se devuelve al cliente y se escribe en
  // cada línea de log, así que no puede transportar saltos de línea ni comillas (log forging).
  private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

  /**
   * Reutiliza el identificador que envíe el cliente —así una petición se sigue de extremo a extremo
   * entre frontend y backend— y genera uno nuevo cuando falta o no supera la validación.
   */
  private String resolveCorrelationId(HttpServletRequest request) {
    String incoming = request.getHeader(CORRELATION_ID_HEADER);
    if (incoming != null && SAFE_CORRELATION_ID.matcher(incoming).matches()) {
      return incoming;
    }
    return UUID.randomUUID().toString();
  }
}
