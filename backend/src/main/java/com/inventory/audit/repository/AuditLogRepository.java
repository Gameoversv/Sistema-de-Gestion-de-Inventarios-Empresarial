package com.inventory.audit.repository;

import com.inventory.audit.domain.AuditLog;
import com.inventory.audit.domain.AuditLog.Action;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

  List<AuditLog> findByPerformedByOrderByCreatedAtDesc(String username);

  Page<AuditLog> findByAction(Action action, Pageable pageable);
}
