package com.unilodge.common.dto;

import com.unilodge.common.enums.UserRole;

import java.util.UUID;

/** Lightweight author card attached to posts and comments. */
public record AuthorDto(
        UUID id,
        String fullName,
        String avatarUrl,
        UserRole role
) {
}
