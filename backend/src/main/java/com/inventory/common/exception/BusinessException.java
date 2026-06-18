package com.inventory.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción de dominio que indica una violación de una regla de negocio (p. ej. stock
 * insuficiente). Se mapea automáticamente a HTTP 422 Unprocessable Entity por {@link
 * com.inventory.common.exception.GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessException extends RuntimeException {

  public BusinessException(String message) {
    super(message);
  }
}
