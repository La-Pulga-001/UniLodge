package com.unilodge.common.dto;

import java.util.UUID;

public record UniversityDto(
        UUID id,
        String name,
        String shortName,
        String city,
        String region,
        Double latitude,
        Double longitude,
        boolean active
) {
}
