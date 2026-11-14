package com.unilodge.common.dto;

import com.unilodge.common.enums.InvitationStatus;

/** Preview of an administrator invitation, shown before accepting it. */
public record InvitePreviewResponse(
        String email,
        String fullName,
        InvitationStatus status,
        boolean valid,
        String message
) {
}
