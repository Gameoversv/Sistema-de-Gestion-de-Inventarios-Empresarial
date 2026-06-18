package com.inventory.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuración que habilita el auditing automático de JPA (para poblar {@code createdAt} y {@code
 * updatedAt} en {@link com.inventory.common.domain.BaseEntity}) y la ejecución asíncrona de métodos
 * anotados con {@code @Async}.
 */
@Configuration
@EnableJpaAuditing
@EnableAsync
public class JpaAuditingConfig {}
