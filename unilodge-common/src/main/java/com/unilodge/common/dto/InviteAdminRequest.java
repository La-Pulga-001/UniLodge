package com.unilodge.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteAdminRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 2, max = 80) String fullName
) {
}
