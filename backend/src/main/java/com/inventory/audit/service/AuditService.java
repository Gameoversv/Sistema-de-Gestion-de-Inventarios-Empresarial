package com.inventory.audit.service;

import com.inventory.audit.domain.AuditLog;
import com.inventory.audit.domain.AuditLog.Action;
import com.inventory.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio que persiste entradas de auditoría manual de forma asíncrona y en una transacción
 * independiente ({@code REQUIRES_NEW}), garantizando que el log se grabe incluso si la transacción
 * principal falla.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(
      String entityType, Long entityId, Action action, String performedBy, String detail) {
    AuditLog entry =
        AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .performedBy(performedBy)
            .detail(detail)
            .build();
    auditLogRepository.save(entry);
  }
}
