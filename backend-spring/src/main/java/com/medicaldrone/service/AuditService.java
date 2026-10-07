package com.medicaldrone.service;

import com.medicaldrone.model.AuditLog;
import com.medicaldrone.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(Long actorUserId, String actorEmail, String action, String entityName, Object entityId, String metadata) {
        auditLogRepository.save(new AuditLog(actorUserId, actorEmail, action, entityName,
                entityId == null ? null : entityId.toString(), metadata));
    }
}
