package com.unilodge.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UniversityRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 30) String shortName,
        @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Boolean active
) {
}
