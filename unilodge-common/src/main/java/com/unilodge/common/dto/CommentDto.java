package com.unilodge.common.dto;

import com.unilodge.common.enums.ModerationStatus;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(
        UUID id,
        String body,
        AuthorDto author,
        Instant createdAt,
        ModerationStatus status
) {
}
