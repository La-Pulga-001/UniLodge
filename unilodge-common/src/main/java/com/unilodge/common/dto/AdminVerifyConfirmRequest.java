package com.unilodge.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminVerifyConfirmRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code
) {
}
