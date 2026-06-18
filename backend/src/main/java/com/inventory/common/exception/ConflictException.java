package com.inventory.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción que señala un conflicto de integridad de datos (p. ej. SKU o nombre de categoría ya
 * existente). Se mapea a HTTP 409 Conflict por {@link
 * com.inventory.common.exception.GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
