package com.unilodge.persistence.repository;

import com.unilodge.persistence.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AdminAuditLog> findTop8ByOrderByCreatedAtDesc();
}
