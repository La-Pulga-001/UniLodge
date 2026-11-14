package com.unilodge.common.dto;

import java.util.UUID;

public record PropertyImageDto(
        UUID id,
        String url,
        boolean cover,
        int sortOrder
) {
}
