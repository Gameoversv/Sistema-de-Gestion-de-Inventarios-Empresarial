package com.inventory.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejador global de excepciones que convierte errores de dominio, validación y acceso en
 * respuestas estructuradas siguiendo el formato RFC 9457 {@code ProblemDetail}. Cubre: recurso no
 * encontrado, conflicto, regla de negocio, validación, acceso denegado y errores inesperados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String PROBLEM_BASE_URI = "https://inventory.api/problems";

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URI + "/not-found"));
    problem.setTitle("Resource Not Found");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(
      ConflictException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URI + "/conflict"));
    problem.setTitle("Conflict");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(
      BusinessException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URI + "/business-error"));
    problem.setTitle("Business Rule Violation");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setType(URI.create(PROBLEM_BASE_URI + "/validation-error"));
    problem.setTitle("Validation Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errors", fieldErrors);
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    problem.setType(URI.create(PROBLEM_BASE_URI + "/access-denied"));
    problem.setTitle("Access Denied");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URI + "/validation-error"));
    problem.setTitle("Constraint Violation");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.badRequest().body(problem);
  }

  /**
   * Campo de ordenamiento desconocido en {@code ?sort=}.
   *
   * <p>Sin este manejador, Spring Data lanza {@code PropertyReferenceException} y el fallback
   * genérico responde 500. Es entrada del usuario —desde F-2, la tabla de productos ordena por la
   * columna que se pulse—, así que un nombre que la entidad no tiene es un 400.
   *
   * <p>El mensaje de la excepción nombra el campo pedido y la entidad; se devuelve tal cual porque
   * no expone nada que el cliente no supiera ya, y sin él el error no es accionable.
   */
  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ProblemDetail> handleUnknownSortProperty(
      PropertyReferenceException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URI + "/invalid-sort-property"));
    problem.setTitle("Invalid Sort Property");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResource(
      NoResourceFoundException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
    problem.setType(URI.create(PROBLEM_BASE_URI + "/not-found"));
    problem.setTitle("Not Found");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {}", request.getRequestURI(), ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    problem.setType(URI.create(PROBLEM_BASE_URI + "/internal-error"));
    problem.setTitle("Internal Server Error");
    problem.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
