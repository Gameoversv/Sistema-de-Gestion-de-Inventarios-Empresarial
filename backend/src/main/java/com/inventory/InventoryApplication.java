package com.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada principal del Sistema de Gestión de Inventarios Empresarial. Arranca el contexto
 * de Spring Boot con todas las configuraciones y componentes definidos en los paquetes {@code
 * com.inventory.*}.
 */
@SpringBootApplication
public class InventoryApplication {

  public static void main(String[] args) {
    SpringApplication.run(InventoryApplication.class, args);
  }
}
