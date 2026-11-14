package com.unilodge.persistence.repository;

import com.unilodge.common.enums.InvitationStatus;
import com.unilodge.persistence.entity.AdminInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, UUID> {

    Optional<AdminInvitation> findByToken(String token);

    boolean existsByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);

    List<AdminInvitation> findAllByOrderByCreatedAtDesc();
}
