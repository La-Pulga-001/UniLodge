package com.unilodge.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String fullName,
        @Size(max = 40) String phone,
        UUID universityId,
        @Size(max = 120) String businessName
) {
}
