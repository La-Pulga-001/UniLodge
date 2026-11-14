package com.unilodge.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.unilodge.common.enums.UserRole;

/** Payload for student or landlord self-registration. */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 80) String fullName,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 40) String phone,
        @NotNull UserRole role,
        java.util.UUID universityId,
        @Size(max = 120) String businessName
) {
}
