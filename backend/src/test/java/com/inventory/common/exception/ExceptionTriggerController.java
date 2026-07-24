package com.inventory.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller that throws each exception handled by {@link GlobalExceptionHandler}. */
@RestController
@RequestMapping("/test-handler")
class ExceptionTriggerController {

  record ValidatedBody(@NotBlank String name) {}

  @GetMapping("/not-found")
  void notFound() {
    throw new ResourceNotFoundException("item not found");
  }

  @GetMapping("/conflict")
  void conflict() {
    throw new ConflictException("item already exists");
  }

  @GetMapping("/business")
  void business() {
    throw new BusinessException("business rule violated");
  }

  @PostMapping("/validate")
  void validate(@Valid @RequestBody ValidatedBody body) {
    // Vacío a propósito: lo que se prueba es @Valid, que rechaza el cuerpo antes de llegar aquí.
    // Si la validación pasa, no hay nada que hacer; si falla, este método nunca se ejecuta.
  }

  @GetMapping("/access-denied")
  void accessDenied() {
    throw new AccessDeniedException("access denied to resource");
  }

  @GetMapping("/constraint-violation")
  void constraintViolation() {
    throw new ConstraintViolationException("must be positive", Set.of());
  }

  @GetMapping("/unknown-sort-property")
  void unknownSortProperty() {
    // Lo que lanza Spring Data cuando ?sort= nombra un campo que la entidad no tiene.
    // TypeInformation.of() y no ClassTypeInformation.from(): esta ultima esta marcada como
    // deprecated for removal en Spring Data y desaparecera en una version proxima.
    throw new PropertyReferenceException("noExiste", TypeInformation.of(Object.class), List.of());
  }

  @GetMapping("/generic-error")
  void genericError() {
    throw new RuntimeException("something unexpected happened");
  }
}
