package com.unilodge.common.dto;

import jakarta.validation.constraints.Size;

/** Optional moderation note when rejecting or suspending content. */
public record AdminActionRequest(
        @Size(max = 500) String note
) {
}
