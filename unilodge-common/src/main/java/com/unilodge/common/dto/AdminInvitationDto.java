package com.unilodge.common.dto;

import com.unilodge.common.enums.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminInvitationDto(
        UUID id,
        String email,
        InvitationStatus status,
        String invitedByName,
        Instant createdAt,
        Instant expiresAt
) {
}
