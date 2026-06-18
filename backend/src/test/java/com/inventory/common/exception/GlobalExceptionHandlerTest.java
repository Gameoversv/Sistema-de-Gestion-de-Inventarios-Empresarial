package com.inventory.common.exception;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExceptionTriggerController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

  @Autowired MockMvc mockMvc;

  @MockBean JwtDecoder jwtDecoder;

  // ── ResourceNotFoundException → 404 ──────────────────────────────────────

  // Verifica que ResourceNotFoundException produce un ProblemDetail con status 404.
  @Test
  void handleNotFound_throwsResourceNotFound_returns404WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/not-found").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("item not found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  // ── ConflictException → 409 ───────────────────────────────────────────────

  // Verifica que ConflictException produce un ProblemDetail con status 409.
  @Test
  void handleConflict_throwsConflict_returns409WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/conflict").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value("item already exists"))
        .andExpect(jsonPath("$.status").value(409));
  }

  // ── BusinessException → 422 ───────────────────────────────────────────────

  // Verifica que BusinessException produce un ProblemDetail con status 422.
  @Test
  void handleBusiness_throwsBusinessException_returns422WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/business").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.title").value("Business Rule Violation"))
        .andExpect(jsonPath("$.detail").value("business rule violated"))
        .andExpect(jsonPath("$.status").value(422));
  }

  // ── MethodArgumentNotValidException → 400 ────────────────────────────────

  // Verifica que un campo vacío en el body genera 400 con errores por campo en la respuesta.
  @Test
  void handleValidation_blankName_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/test-handler/validate")
                .with(jwt().jwt(j -> j.subject("user")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.detail").value("Validation failed"))
        .andExpect(jsonPath("$.errors.name").exists());
  }

  // Verifica que un campo requerido faltante en el body genera 400 con status correcto.
  @Test
  void handleValidation_missingField_returns400WithStatus() throws Exception {
    mockMvc
        .perform(
            post("/test-handler/validate")
                .with(jwt().jwt(j -> j.subject("user")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  // ── AccessDeniedException → 403 ───────────────────────────────────────────

  // Verifica que AccessDeniedException produce un ProblemDetail con status 403.
  @Test
  void handleAccessDenied_throwsAccessDenied_returns403WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/access-denied").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access Denied"))
        .andExpect(jsonPath("$.detail").value("Access denied"))
        .andExpect(jsonPath("$.status").value(403));
  }

  // ── ConstraintViolationException → 400 ───────────────────────────────────

  // Verifica que ConstraintViolationException produce un ProblemDetail con status 400.
  @Test
  void handleConstraint_throwsConstraintViolation_returns400WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/constraint-violation").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Constraint Violation"))
        .andExpect(jsonPath("$.status").value(400));
  }

  // ── NoResourceFoundException → 404 (unmapped path) ───────────────────────

  // Verifica que una ruta que no existe en el servidor retorna 404.
  @Test
  void handleNoResource_unknownPath_returns404() throws Exception {
    mockMvc
        .perform(
            get("/test-handler/this-path-does-not-exist").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isNotFound());
  }

  // ── Generic Exception → 500 ───────────────────────────────────────────────

  // Verifica que una excepción inesperada produce un ProblemDetail con status 500.
  @Test
  void handleGeneric_unexpectedException_returns500WithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/test-handler/generic-error").with(jwt().jwt(j -> j.subject("user"))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.detail").value("Internal server error"))
        .andExpect(jsonPath("$.status").value(500));
  }

  // ── Unauthenticated access ────────────────────────────────────────────────

  // Verifica que cualquier endpoint del handler retorna 401 cuando no hay autenticación.
  @Test
  void anyEndpoint_anonymous_returns401() throws Exception {
    mockMvc.perform(get("/test-handler/not-found")).andExpect(status().isUnauthorized());
  }
}
