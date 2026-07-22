package com.inventory.audit.repository;

import com.inventory.audit.domain.AuditLog;
import com.inventory.audit.domain.AuditLog.Action;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link com.inventory.audit.domain.AuditLog}. Provee consultas para filtrar
 * registros por entidad, usuario que realizó la acción y tipo de acción, con soporte de paginación.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

  List<AuditLog> findByPerformedByOrderByCreatedAtDesc(String username);

  Page<AuditLog> findByAction(Action action, Pageable pageable);
}
