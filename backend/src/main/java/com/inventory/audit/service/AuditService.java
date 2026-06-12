package com.inventory.audit.service;

import com.inventory.audit.domain.AuditLog;
import com.inventory.audit.domain.AuditLog.Action;
import com.inventory.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
