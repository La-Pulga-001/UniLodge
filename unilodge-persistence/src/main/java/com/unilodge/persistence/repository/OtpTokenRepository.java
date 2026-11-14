package com.unilodge.persistence.repository;

import com.unilodge.common.enums.OtpPurpose;
import com.unilodge.persistence.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);
}
