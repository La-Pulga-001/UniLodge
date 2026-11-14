package com.unilodge.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminInviteAcceptRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
