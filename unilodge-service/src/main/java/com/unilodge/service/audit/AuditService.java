package com.unilodge.service.audit;

import com.unilodge.common.dto.AuditLogDto;
import com.unilodge.common.dto.PagedResponse;
import com.unilodge.common.enums.AuditAction;
import com.unilodge.persistence.entity.AdminAuditLog;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.AdminAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Writes and reads the administrator audit trail. */
@Service
public class AuditService {

    private final AdminAuditLogRepository repository;

    public AuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(User actor, AuditAction action, String targetType, UUID targetId,
                       String targetLabel, String details) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : targetId.toString());
        log.setTargetLabel(targetLabel);
        log.setDetails(details != null && details.length() > 2000 ? details.substring(0, 2000) : details);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogDto> list(int page, int size) {
        Page<AdminAuditLog> result = repository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<AuditLogDto> content = result.getContent().stream().map(this::toDto).toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> recent() {
        return repository.findTop8ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    private AuditLogDto toDto(AdminAuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getActor() == null ? "System" : log.getActor().getFullName(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetLabel(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
