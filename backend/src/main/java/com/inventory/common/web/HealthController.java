package com.inventory.common.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de verificación de estado del servicio. Devuelve {@code status: UP} y el
 * timestamp actual. Accesible sin autenticación para sondas de balanceadores de carga.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  @GetMapping
  public Map<String, Object> health() {
    return Map.of("status", "UP", "timestamp", Instant.now().toString());
  }
}
